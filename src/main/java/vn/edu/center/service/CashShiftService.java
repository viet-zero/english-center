package vn.edu.center.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CashShiftService {
    private final JdbcTemplate sql;
    public CashShiftService(JdbcTemplate sql) { this.sql=sql; }

    // All shift writes lock the employee before the shift. Payment callers hold this until commit.
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public String requireOpen(String employee) {
        lockEmployee(employee);
        return sql.queryForList("SELECT id FROM ca_thu_tien WHERE employee_id=? AND status='OPEN' FOR UPDATE",
                String.class,employee).stream().findFirst()
                .orElseThrow(()->new IllegalStateException("Hãy mở ca thu tiền trước khi ghi nhận thu hoặc hoàn tiền."));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> open(String employee,String id,BigDecimal openingCash) {
        lockEmployee(employee);
        var previous=sql.queryForList("SELECT * FROM ca_thu_tien WHERE id=? FOR UPDATE",id);
        if (!previous.isEmpty()) {
            var shift=previous.getFirst();
            if (!employee.equals(shift.get("employee_id"))) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (openingCash.compareTo((BigDecimal)shift.get("opening_cash"))!=0) {
                throw new IllegalStateException("Mã mở ca đã được dùng với số tiền khác.");
            }
            return shift;
        }
        if (sql.queryForObject("SELECT COUNT(*) FROM ca_thu_tien WHERE employee_id=? AND status='OPEN'",
                Long.class,employee)>0) throw new IllegalStateException("Bạn đã có ca đang mở.");
        sql.update("INSERT INTO ca_thu_tien(id,employee_id,opening_cash) VALUES (?,?,?)",id,employee,openingCash);
        return sql.queryForMap("SELECT * FROM ca_thu_tien WHERE id=?",id);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> shifts(String employee) {
        var rows=sql.queryForList("SELECT * FROM ca_thu_tien WHERE employee_id=? ORDER BY opened_at DESC,id",employee);
        Map<String,Object> current=new LinkedHashMap<>();
        for (var row:rows) {
            if ("OPEN".equals(row.get("status"))) {
                current=new LinkedHashMap<>(row);
                current.putAll(totals((String)row.get("id"),(BigDecimal)row.get("opening_cash")));
                break;
            }
        }
        return Map.of("current",current,"history",rows);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> close(String employee,String id,BigDecimal actualCash,String note) {
        lockEmployee(employee);
        var shift=sql.queryForList("SELECT * FROM ca_thu_tien WHERE id=? AND employee_id=? FOR UPDATE",id,employee)
                .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        String normalized=note==null ? "" : note.strip();
        if ("CLOSED".equals(shift.get("status"))) {
            if (actualCash.compareTo((BigDecimal)shift.get("actual_cash"))==0
                    && normalized.equals(shift.get("close_note"))) return shift;
            throw new IllegalStateException("Ca đã chốt, không được sửa số tiền hoặc ghi chú.");
        }
        var summary=totals(id,(BigDecimal)shift.get("opening_cash"));
        BigDecimal difference=actualCash.subtract(summary.get("expected_cash"));
        if (difference.signum()!=0 && normalized.isBlank()) {
            throw new IllegalStateException("Có chênh lệch quỹ. Bắt buộc ghi lý do trước khi chốt ca.");
        }
        sql.update("""
                UPDATE ca_thu_tien SET status='CLOSED',closed_at=CURRENT_TIMESTAMP,cash_received=?,cash_refunded=?,
                    expected_cash=?,actual_cash=?,difference=?,close_note=? WHERE id=?
                """,summary.get("cash_received"),summary.get("cash_refunded"),summary.get("expected_cash"),
                actualCash,difference,normalized,id);
        return sql.queryForMap("SELECT * FROM ca_thu_tien WHERE id=?",id);
    }

    private Map<String,BigDecimal> totals(String id,BigDecimal opening) {
        BigDecimal received=sql.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM giao_dich WHERE shift_id=? AND method='CASH' AND status='SUCCESS'
                """,BigDecimal.class,id);
        BigDecimal refunded=sql.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM hoan_tien_demo WHERE shift_id=? AND method='CASH'
                """,BigDecimal.class,id);
        return Map.of("cash_received",received,"cash_refunded",refunded,"expected_cash",opening.add(received).subtract(refunded));
    }

    private void lockEmployee(String employee) {
        if (sql.queryForList("SELECT id FROM nhan_vien WHERE id=? AND status='ACTIVE' AND deleted=false FOR UPDATE",
                String.class,employee).isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> revenue(LocalDate from,LocalDate to) {
        if (to.isBefore(from) || from.getYear()<1000 || to.getYear()>9998) {
            throw new IllegalArgumentException("Khoảng ngày không hợp lệ.");
        }
        // payOS receipts remain the original cash-flow event when reconciled into an invoice later.
        var days=sql.queryForList("""
                SELECT DATE(event_at) AS day,SUM(received) AS received,SUM(refunded) AS refunded,
                       SUM(cash_received) AS cashReceived,SUM(cash_refunded) AS cashRefunded,
                       SUM(pending_review) AS pendingReview,SUM(received-refunded) AS netReceived
                FROM (
                    SELECT g.paid_at AS event_at,g.amount AS received,0 AS refunded,
                           IF(g.method='CASH',g.amount,0) AS cash_received,0 AS cash_refunded,0 AS pending_review
                    FROM giao_dich g WHERE g.status='SUCCESS'
                      AND NOT EXISTS(SELECT 1 FROM bien_nhan_payos r JOIN don_payos o ON o.order_code=r.order_code
                          WHERE r.reference=g.reference_code AND COALESCE(r.resolved_invoice_id,o.invoice_id)=g.invoice_id
                            AND (r.reason IS NULL OR r.resolution='TRANSFER'))
                    UNION ALL
                    SELECT r.received_at,r.amount,0,0,0,
                           IF(r.reason IS NOT NULL AND r.resolution IS NULL,r.amount,0)
                    FROM bien_nhan_payos r
                    UNION ALL
                    SELECT f.created_at,0,f.amount,0,IF(f.method='CASH',f.amount,0),0 FROM hoan_tien_demo f
                    UNION ALL
                    SELECT r.resolved_at,0,r.amount,0,0,0 FROM bien_nhan_payos r WHERE r.resolution='REFUND_DEMO'
                ) movements
                WHERE event_at>=? AND event_at<? GROUP BY DATE(event_at) ORDER BY day
                """,from.atStartOfDay(),to.plusDays(1).atStartOfDay());
        var summary=new LinkedHashMap<String,BigDecimal>();
        for (String key:List.of("received","refunded","cashReceived","cashRefunded","pendingReview","netReceived")) {
            BigDecimal total=BigDecimal.ZERO;
            for (var day:days) total=total.add((BigDecimal)day.get(key));
            summary.put(key,total);
        }
        return Map.of("from",from,"to",to,"summary",summary,"days",days);
    }
}
