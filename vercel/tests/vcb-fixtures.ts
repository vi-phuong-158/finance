// Synthetic semantics inside the already-supported VCB envelope only.
// These labels are NOT evidence of actual bank-specific QR/POS formats.
export const syntheticAccount = '9'.repeat(12);
const notification = (amount: string, balance: string, ref: string) =>
  `Số dư TK VCB ${syntheticAccount} ${amount} VND lúc 01-01-2026 12:00:00. Số dư ${balance} VND. Ref MBVCB.FIXTURE.${ref}`;

export const vcbFixtures = [
  { name: 'CHI transfer', text: notification('-100000', '900000', 'NGƯỜI NHẬN GIẢ chuyen khoan'), type: 'CHI', amount: 100000, balance: 900000 },
  { name: 'THU transfer', text: notification('+100000', '1100000', 'NGƯỜI GỬI GIẢ'), type: 'THU', amount: 100000, balance: 1100000 },
  { name: 'QR description in supported envelope', text: notification('-1,000', '999,000', 'QR TEST SUPERMARKET'), type: 'CHI', amount: 1000, balance: 999000 },
  { name: 'POS description in supported envelope', text: notification('-1.000.000', '9.000.000', 'POS NHÀ THUỐC LONG CHÂU'), type: 'CHI', amount: 1000000, balance: 9000000 },
  { name: 'zero balance', text: notification('-100000', '0', 'TEST MERCHANT'), type: 'CHI', amount: 100000, balance: 0 },
  { name: 'long description and identifier', text: notification('-10,000', '90,000', `NỘI DUNG GIẢ ${'ghi chu '.repeat(100)} TK 9999-999-999`), type: 'CHI', amount: 10000, balance: 90000 },
  { name: 'newlines/whitespace/NFD', text: notification('+1.000.000', '2.000.000', 'ĐỐI TÁC GIẢ').replaceAll(' ', ' \n\t\u00a0').normalize('NFD'), type: 'THU', amount: 1000000, balance: 2000000 },
] as const;

export const unknownVcbFixtures = [
  `GD TK ${syntheticAccount} -100,000 VND POS FORMAT UNKNOWN`,
  notification('-1,000.000', '900000', 'MIXED GROUPING'),
  notification('-100000', '900000', 'INVALID TIME').replace('12:00:00', '12:00:001'),
  notification('-100000', '900000', 'INVALID DATE').replace('01-01-2026', '31-02-2026'),
  notification('-100000', '900000', 'MULTIPLE') + ' ' + notification('+100000', '1000000', 'MULTIPLE'),
];
