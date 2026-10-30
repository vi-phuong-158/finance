// DAILY_ACT365_25_V1: actual closing balances, actual days / 365; settlements on the 25th.
export function trancheFormulas(r: number): string[] {
  const cfg = "'Cấu hình'!", pay = "'Giao dịch'!";
  const dates = `${pay}$A$2:$A$50001`, amounts = `${pay}$E$2:$E$50001`;
  const loans = `${pay}$B$2:$B$50001`, kinds = `${pay}$C$2:$C$50001`;
  const repaid = (date: string) => `SUMIFS(${amounts},${loans},B${r},${kinds},"Thu gốc",${dates},">0",${dates},"<="&MIN(${date},${cfg}$B$2))`;
  const balance = (date: string) => `MAX(0,C${r}-MAX(0,${repaid(date)}-H${r}))`;
  const integral = (from: string, to: string) => `=IF(A${r}="","",IF(E${r}>${cfg}$B$2,0,LET(lo,MAX(E${r},${from}),hi,MIN(${to},IF(F${r}="",${to},F${r})),IF(hi<=lo,0,LET(cuts,SORT(UNIQUE(VSTACK(lo,hi,IFERROR(FILTER(${dates},${loans}=B${r},${kinds}="Thu gốc",${dates}>lo,${dates}<hi,${dates}<=${cfg}$B$2),lo)))),SUM(MAP(SEQUENCE(ROWS(cuts)-1),LAMBDA(idx,${balance('INDEX(cuts,idx)')}*(INDEX(cuts,idx+1)-INDEX(cuts,idx))*D${r}/365))))))))`;
  return [
    `=IF(A${r}="","",E${r})`,
    `=IF(A${r}="","",SUMIFS($C$2:$C$300,$B$2:$B$300,B${r},$E$2:$E$300,"<"&E${r})+SUMIFS($C$2:$C$300,$B$2:$B$300,B${r},$E$2:$E$300,E${r},$A$2:$A$300,"<"&A${r}))`,
    `=IF(A${r}="","",IF(E${r}>${cfg}$B$7,0,${balance(`${cfg}$B$7`)}))`,
    `=IF(A${r}="","",LET(cutoff,MIN(${cfg}$B$2,IF(F${r}="",${cfg}$B$2,F${r})),lastcut,DATE(YEAR(cutoff),MONTH(cutoff)-IF(DAY(cutoff)<25,1,0),25),firstcut,DATE(YEAR(E${r}),MONTH(E${r})-IF(DAY(E${r})<25,1,0),25),MAX(0,(YEAR(lastcut)-YEAR(firstcut))*12+MONTH(lastcut)-MONTH(firstcut))))`,
    integral(`E${r}`,`${cfg}$B$7`),
    integral(`${cfg}$B$7`,`${cfg}$B$8`),
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
