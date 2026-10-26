// These formulas and the read engine implement MONTHLY_25_V1; no daily accrual.
export function trancheFormulas(r: number): string[] {
  const cfg = "'Cấu hình'!", pay = "'Giao dịch'!";
  const repaid = (date: string) => `SUMIFS(${pay}$E:$E,${pay}$B:$B,B${r},${pay}$C:$C,"Thu gốc",${pay}$A:$A,">0",${pay}$A:$A,"<="&${date})`;
  const opening = (date: string) => `MAX(0,C${r}-MAX(0,${repaid(date)}-H${r}))`;
  return [
    `=IF(A${r}="","",DATE(YEAR(E${r}),MONTH(E${r})+IF(DAY(E${r})>25,1,0),25))`,
    `=IF(A${r}="","",SUMIFS($C$2:$C$300,$B$2:$B$300,B${r},$E$2:$E$300,"<"&E${r})+SUMIFS($C$2:$C$300,$B$2:$B$300,B${r},$E$2:$E$300,E${r},$A$2:$A$300,"<"&A${r}))`,
    `=IF(A${r}="","",IF(G${r}>${cfg}$B$7,0,${opening(`${cfg}$B$7`)}))`,
    `=IF(A${r}="","",LET(cutoff,MIN(${cfg}$B$2,IF(F${r}="",${cfg}$B$2,F${r})),lastcut,DATE(YEAR(cutoff),MONTH(cutoff)-IF(DAY(cutoff)<25,1,0),25),MAX(0,(YEAR(lastcut)-YEAR(G${r}))*12+MONTH(lastcut)-MONTH(G${r}))))`,
    `=IF(A${r}="","",IF(J${r}=0,0,SUM(MAP(SEQUENCE(J${r}),LAMBDA(idx,${opening(`EDATE(G${r},idx-1)`)}*D${r}/12)))))`,
    `=IF(A${r}="","",IF(AND(G${r}<=${cfg}$B$7,OR(F${r}="",F${r}>=${cfg}$B$8)),I${r}*D${r}/12,0))`,
  ];
}
export function loanFormulas(r: number): string[] {
  const cfg = "'Cấu hình'!", t = "'Đợt giải ngân'!", p = "'Giao dịch'!";
  const sums = (column: string) => `SUMIFS(${t}$${column}$2:$${column}$300,${t}$B$2:$B$300,A${r})`;
  const paid = (kind: string) => `SUMIFS(${p}$E:$E,${p}$B:$B,A${r},${p}$C:$C,"${kind}",${p}$A:$A,">0",${p}$A:$A,"<="&${cfg}$B$2)`;
  const wrap = (formula: string) => `=IF(A${r}="","",IF(COUNTIF(${t}$B$2:$B$300,A${r})=0,NA(),${formula}))`;
  return [
    wrap(`ROUND(${sums('L')},0)`),
    wrap(`MAXIFS(${t}$J$2:$J$300,${t}$B$2:$B$300,A${r})`),
    wrap(`ROUND(${sums('K')},0)`),
    wrap(paid('Thu lãi')),
    wrap(`MAX(0,I${r}-J${r})`),
    wrap(paid('Thu gốc')),
    wrap(`MAX(0,SUMIFS(${t}$C$2:$C$300,${t}$B$2:$B$300,A${r},${t}$E$2:$E$300,"<="&${cfg}$B$2)-L${r})`),
    wrap(`IF(${cfg}$B$2<E${r},"Chưa giải ngân",IF(M${r}=0,"Đã tất toán",IF(AND(F${r}<>"",F${r}<${cfg}$B$2),"Quá hạn",IF(AND(F${r}<>"",F${r}-${cfg}$B$2<=7),"Sắp đến hạn","Đang vay"))))`),
  ];
}
export function loanPeriodFormulas(r: number): string[] {
  const p = "'Giao dịch'!", cfg = "'Cấu hình'!";
  return [
    `=IF(A${r}="","",SUMIFS(${p}$E:$E,${p}$B:$B,A${r},${p}$C:$C,"Thu lãi",${p}$A:$A,">="&${cfg}$B$7,${p}$A:$A,"<="&${cfg}$B$2))`,
    `=IF(A${r}="","",MAX(0,J${r}-I${r}))`,
    `=IF(A${r}="","",MAX(0,G${r}-Q${r}))`,
    `=IF(A${r}="","",${cfg}$B$7)`,
    `=IF(A${r}="","",${cfg}$B$8)`,
  ];
}
