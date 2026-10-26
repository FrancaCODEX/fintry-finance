package com.fintry.report;

import org.springframework.stereotype.Component;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/** Exports the same tenant-scoped, filtered rows as the report. Never emits spreadsheet formulas. */
@Component
public class ReportExporter {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String[] HEADERS={"Descrição","Tipo","Situação","Conta","Categoria","Cliente / fornecedor","Vencimento","Pagamento","Valor","Parcela / repetição"};
    private String date(LocalDate d){return d==null?"":DATE.format(d);}
    private String money(BigDecimal v){return "R$ "+String.format(new Locale("pt","BR"),"%,.2f",v);}
    private String status(com.fintry.finance.FinancialEntry.Status s){return switch(s){case PAID->"Realizado";case PARTIAL->"Parcial";case PENDING->"Pendente";case CANCELLED->"Cancelado";};}
    private String type(com.fintry.finance.FinancialEntry.Type t){return t==com.fintry.finance.FinancialEntry.Type.INCOME?"Receita":"Despesa";}
    private String caption(ReportService.Export d){return date(d.filters().start())+" a "+date(d.filters().end())+" · Por "+(d.filters().basis()==ReportService.Basis.PAYMENT?"pagamento":"vencimento");}
    private String filters(ReportService.Export d){var f=d.filters();return "Conta: "+d.accountName()+" | Tipo: "+(f.type()==null?"Todos":type(f.type()))+" | Situação: "+(f.basis()==ReportService.Basis.PAYMENT?"Realizado":f.status()==null?"Ativos":status(f.status()))+" | Categoria: "+(f.category()==null||f.category().isBlank()?"Todas":f.category())+" | Contato: "+(f.contact()==null||f.contact().isBlank()?"Todos":f.contact());}
    private String[] values(ReportService.Row r){return new String[]{r.description(),type(r.type()),status(r.status()),r.accountName(),r.category(),r.contactName(),date(r.dueDate()),date(r.paymentDate()),r.amount().toPlainString().replace('.',','),r.sequenceNumber()==null?"":r.sequenceNumber()+"/"+r.seriesCount()};}
    private String csvCell(String value){
        if(value==null)value="";
        // Quoting alone does not prevent spreadsheet formula execution on CSV import.
        String leading=value.stripLeading();
        if(!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0))>=0 || !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0))>=0)value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
    public byte[] csv(ReportService.Export data){
        StringBuilder out=new StringBuilder("\ufeff");
        out.append(String.join(";",Arrays.stream(HEADERS).map(this::csvCell).toList())).append("\r\n");
        for(var row:data.rows())out.append(String.join(";",Arrays.stream(values(row)).map(this::csvCell).toList())).append("\r\n");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    private String xml(String value){
        if(value==null)return "";var clean=new StringBuilder();
        value.codePoints().filter(c->c==9||c==10||c==13||c>=32&&c<=0xd7ff||c>=0xe000&&c<=0xfffd||c>=0x10000&&c<=0x10ffff).forEach(clean::appendCodePoint);
        return clean.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
    }
    private void entry(ZipOutputStream zip,String path,String content)throws IOException {zip.putNextEntry(new ZipEntry(path));zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    private void textCell(StringBuilder sheet,String ref,String value,int style){sheet.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value)).append("</t></is></c>");}
    private void numberCell(StringBuilder sheet,String ref,BigDecimal amount){sheet.append("<c r=\"").append(ref).append("\" s=\"1\"><v>").append(amount.toPlainString()).append("</v></c>");}
    public byte[] xlsx(ReportService.Export data)throws IOException {
        StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"11\" topLeftCell=\"A12\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>");
        int[] widths={40,14,15,24,24,30,15,15,20,20};for(int i=0;i<widths.length;i++)sheet.append("<col min=\"").append(i+1).append("\" max=\"").append(i+1).append("\" width=\"").append(widths[i]).append("\" customWidth=\"1\"/>");sheet.append("</cols><sheetData>");
        String[] info={"Fintry · Relatório financeiro",data.companyName(),caption(data),filters(data)};
        for(int i=0;i<info.length;i++){sheet.append("<row r=\"").append(i+1).append("\">");textCell(sheet,"A"+(i+1),info[i],i==0?3:0);sheet.append("</row>");}
        String[] labels={"Receitas realizadas","Despesas realizadas","Resultado de caixa realizado","Receitas pendentes","Despesas pendentes"};
        BigDecimal[] totals={data.totals().income(),data.totals().expense(),data.totals().result(),data.totals().pendingIncome(),data.totals().pendingExpense()};
        for(int i=0;i<labels.length;i++){int row=i+5;sheet.append("<row r=\"").append(row).append("\">");textCell(sheet,"A"+row,labels[i],0);numberCell(sheet,"B"+row,totals[i]);sheet.append("</row>");}
        sheet.append("<row r=\"11\">");for(int i=0;i<HEADERS.length;i++)textCell(sheet,""+(char)('A'+i)+11,HEADERS[i],2);sheet.append("</row>");
        int rowNumber=12;for(var row:data.rows()) {sheet.append("<row r=\"").append(rowNumber).append("\">");String[] values=values(row);for(int i=0;i<values.length;i++){String ref=""+(char)('A'+i)+rowNumber;if(i==8)numberCell(sheet,ref,row.amount());else textCell(sheet,ref,values[i],0);}sheet.append("</row>");rowNumber++;}
        sheet.append("</sheetData><autoFilter ref=\"A11:J").append(Math.max(11,rowNumber-1)).append("\"/><mergeCells count=\"4\"><mergeCell ref=\"A1:J1\"/><mergeCell ref=\"A2:J2\"/><mergeCell ref=\"A3:J3\"/><mergeCell ref=\"A4:J4\"/></mergeCells></worksheet>");
        var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out,StandardCharsets.UTF_8)){
            entry(zip,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
            entry(zip,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            entry(zip,"xl/workbook.xml","<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Relatório\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(zip,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            entry(zip,"xl/styles.xml","<?xml version=\"1.0\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"&quot;R$&quot; #,##0.00\"/></numFmts><fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"16\"/><name val=\"Calibri\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF18785A\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/><xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
            entry(zip,"xl/worksheets/sheet1.xml",sheet.toString());
        }return out.toByteArray();
    }
    private String pdfText(String value){if(value==null)return "";return new String(value.replaceAll("[\\p{Cntrl}]"," ").getBytes(Charset.forName("windows-1252")),Charset.forName("windows-1252"));}
    private String fit(String value,PDFont font,float size,float width)throws IOException {String text=pdfText(value);if(font.getStringWidth(text)/1000*size<=width)return text;while(!text.isEmpty() && font.getStringWidth(text+"...")/1000*size>width)text=text.substring(0,text.length()-1);return text+"...";}
    private void text(PDPageContentStream stream,PDFont font,float size,float x,float y,String value)throws IOException {stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(x,y);stream.showText(pdfText(value));stream.endText();}
    public byte[] pdf(ReportService.Export data)throws IOException {
        try(var document=new PDDocument();var out=new ByteArrayOutputStream()) {
            var regular=new PDType1Font(Standard14Fonts.FontName.HELVETICA);var bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            int pageNumber=0,index=0;do {
                var page=new PDPage(new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()));document.addPage(page);pageNumber++;
                try(var stream=new PDPageContentStream(document,page)) {
                    float top=page.getMediaBox().getHeight();stream.setNonStrokingColor(16/255f,45/255f,42/255f);
                    text(stream,bold,22,32,top-38,"Fintry · Relatório financeiro");text(stream,bold,11,32,top-59,fit(data.companyName(),bold,11,770));
                    text(stream,regular,10,32,top-78,caption(data));text(stream,regular,8,32,top-95,fit(filters(data),regular,8,770));
                    text(stream,regular,10,32,top-116,"Receitas realizadas: "+money(data.totals().income())+"    Despesas realizadas: "+money(data.totals().expense())+"    Resultado de caixa: "+money(data.totals().result()));
                    text(stream,regular,9,32,top-133,"Pendentes a receber: "+money(data.totals().pendingIncome())+"    Pendentes a pagar: "+money(data.totals().pendingExpense())+"    "+data.rows().size()+" lançamento(s)");
                    float y=top-159;text(stream,bold,9,32,y,"DESCRIÇÃO / CATEGORIA / CONTATO");text(stream,bold,9,330,y,"DATA DO FILTRO");text(stream,bold,9,440,y,"SITUAÇÃO");text(stream,bold,9,530,y,"CONTA");text(stream,bold,9,730,y,"VALOR");y-=20;
                    if(data.rows().isEmpty())text(stream,regular,11,32,y,"Nenhum lançamento encontrado para os filtros aplicados.");
                    while(index<data.rows().size() && y>55) {
                        var row=data.rows().get(index++);String occurrence=row.sequenceNumber()==null?"":" · "+row.sequenceNumber()+"/"+row.seriesCount();
                        text(stream,bold,9,32,y,fit(row.description()+occurrence,bold,9,285));text(stream,regular,9,330,y,date(data.filters().basis()==ReportService.Basis.PAYMENT?row.paymentDate():row.dueDate()));text(stream,regular,9,440,y,status(row.status()));text(stream,regular,9,530,y,fit(row.accountName(),regular,9,180));String amount=money(row.amount());float amountSize=Math.min(9,80f/(bold.getStringWidth(pdfText(amount))/1000f));text(stream,bold,amountSize,730,y,amount);
                        text(stream,regular,8,32,y-12,fit(type(row.type())+" · "+row.category()+(row.contactName()==null?"":" · "+row.contactName()),regular,8,285));text(stream,regular,8,330,y-12,"Venc.: "+date(row.dueDate())+" · Baixa: "+date(row.paymentDate()));
                        stream.setStrokingColor(.88f,.91f,.88f);stream.moveTo(32,y-20);stream.lineTo(810,y-20);stream.stroke();y-=36;
                    }
                    text(stream,regular,8,32,24,"Valores em BRL. Transferências excluídas. Resultado de caixa não representa lucro contábil.");text(stream,regular,8,730,24,"Página "+pageNumber);
                }
            }while(index<data.rows().size());document.save(out);return out.toByteArray();
        }
    }
}
