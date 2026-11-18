import assert from 'node:assert/strict';
import { test } from 'node:test';
import { passwordError } from '../src/services/passwordRules.js';

test('password replacement checks confirmation, complexity and BCrypt UTF-8 limit', () => {
  assert.equal(passwordError('UpdatedDemo2026!', 'UpdatedDemo2026!'), '');
  assert.ok(passwordError('UpdatedDemo2026!', 'different'));
  assert.ok(passwordError('weakpassword', 'weakpassword'));
  assert.ok(passwordError('Aa1', 'Aa1'));
  const unicodePassword = 'Aa1' + 'ắ'.repeat(24);
  assert.ok(unicodePassword.length < 72);
  assert.ok(passwordError(unicodePassword, unicodePassword));
});
