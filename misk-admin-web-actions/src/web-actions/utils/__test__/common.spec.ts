import { matchNextWhitespace } from '@web-actions/utils/common';

test('ws', () => {
  expect(matchNextWhitespace('  ', 0)).toBe(2);
});
