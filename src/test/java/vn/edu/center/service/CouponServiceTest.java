package vn.edu.center.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CouponServiceTest {
    @Test
    void sameStudentCannotReuseSharedCouponEvenWhenCampaignHasCapacity() {
        var sql=mock(JdbcTemplate.class,withSettings().mockMaker("mock-maker-subclass"));
        when(sql.queryForList(anyString(),eq("SAVE"))).thenReturn(List.of(Map.of(
                "id","coupon","course_id","course","amount",2000,"valid",true,"status","ACTIVE","max_uses",10)));
        when(sql.queryForObject(contains("student_id"),eq(Long.class),eq("coupon"),eq("student"))).thenReturn(1L);
        var service=new CouponService(sql);
        var failure=assertThrows(IllegalStateException.class,()->service.price("student","course",10000,"save"));
        assertTrue(failure.getMessage().contains("đã sử dụng"));
        verify(sql,never()).update(anyString(),any(Object[].class));
    }

    @Test
    void checkoutWithoutCouponRetainsTheOriginalTuitionAndDoesNotTouchUsage() {
        var sql=mock(JdbcTemplate.class,withSettings().mockMaker("mock-maker-subclass"));
        var pricing=new CouponService(sql).price("student","course",10000,null);
        assertEquals(10000,pricing.due());
        assertEquals(0,pricing.discount());
        assertNull(pricing.couponId());
        verifyNoInteractions(sql);
    }
}
