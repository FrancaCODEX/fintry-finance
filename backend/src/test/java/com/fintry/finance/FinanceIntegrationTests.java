package com.fintry.finance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP + session + CSRF + migrated relational database, rather than mocked repositories. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.profiles.default=test")
class FinanceIntegrationTests {
    @LocalServerPort int port;
    static final AtomicLong DOCUMENT = new AtomicLong(10000000000000L);
    final JsonMapper json = JsonMapper.builder().build();
    String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
    String month = today.substring(0,7);
    class Browser {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String token, header;
        HttpResponse<String> call(String method,String path,Object body,boolean csrf) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path));
            if(csrf && token!=null) request.header(header,token);
            if(body!=null)request.header("Content-Type","application/json");
            return client.send(request.method(method,body==null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode request(String method,String path,Object body,int status) throws Exception {
            var response=call(method,path,body,true);
            assertEquals(status,response.statusCode(),response.body());
            return response.body().isBlank() ? null : json.readTree(response.body());
        }
        void csrf() throws Exception {
            var data=request("GET","/auth/csrf",null,200);token=data.get("token").asText();header=data.get("headerName").asText();
        }
        Browser signup() throws Exception {
            csrf();var email=UUID.randomUUID()+"@example.com";
            request("POST","/auth/register",Map.of("name","Usuário teste","companyName","Empresa de teste",
                "document",String.valueOf(DOCUMENT.incrementAndGet()),"email",email,"password","Senha-segura-123"),201);
            request("POST","/auth/login",Map.of("email",email,"password","Senha-segura-123"),200);csrf();return this;
        }
        String account(String name,String balance) throws Exception {
            return request("POST","/finance/accounts",Map.of("name",name,"initialBalance",balance),201).get("id").asText();
        }
        Map<String,Object> entry(String account, boolean paid, String amount, String type) {
            var data=new HashMap<String,Object>();data.put("accountId",account);data.put("description","Lançamento teste");
            data.put("category","Serviços");data.put("type",type);data.put("amount",amount);data.put("dueDate",today);
            data.put("paid",paid);if(paid)data.put("paymentDate",today);return data;
        }
        JsonNode summary() throws Exception { return request("GET","/finance/summary?month="+month,null,200); }
    }
    void amount(String expected,JsonNode actual) { assertEquals(0,new java.math.BigDecimal(expected).compareTo(actual.decimalValue())); }

    Map<String,Object> contact(String name,String kind,String document,boolean active) {
        return Map.of("name",name,"kind",kind,"document",document,"email","contato@example.com","phone","11999999999","notes","Cadastro de teste","active",active);
    }
    @Test void directoryAndPendingEditPreserveFinancialHistory() throws Exception {
        var b=new Browser().signup();var bank=b.account("Banco","500");
        var person=b.request("POST","/contacts",contact("Cliente original","CUSTOMER","123.456.789-00",true),201);
        String personId=person.get("id").asText();
        assertEquals("12345678900",person.get("document").asText());
        var category=b.request("POST","/categories",Map.of("name","Consultoria","kind","INCOME"),201);
        String categoryId=category.get("id").asText();
        var input=b.entry(bank,false,"100","INCOME");input.put("categoryId",categoryId);input.put("contactId",personId);input.remove("category");
        var entry=b.request("POST","/finance/entries",input,201);String id=entry.get("id").asText();
        assertEquals("Consultoria",entry.get("category").asText());assertEquals("Cliente original",entry.get("contactName").asText());
        input.put("amount","175.50");input.put("description","Projeto ajustado");
        var updated=b.request("PUT","/finance/entries/"+id,input,200);
        amount("175.50",updated.get("amount"));amount("500",b.summary().get("balance"));amount("175.50",b.summary().get("pendingIncome"));
        b.request("PUT","/contacts/"+personId,contact("Cliente renomeado","CUSTOMER","12345678900",false),200);
        b.request("PUT","/categories/"+categoryId,Map.of("name","Consultoria renomeada","kind","INCOME","active",false),200);
        var history=b.request("GET","/finance/entries",null,200).get("content").get(0);
        assertEquals("Cliente original",history.get("contactName").asText());assertEquals("Consultoria",history.get("category").asText());
        assertEquals(0,b.request("GET","/contacts?active=true",null,200).get("totalElements").asInt());
        assertEquals(1,b.request("GET","/contacts?active=false&kind=CUSTOMER&search=renomeado",null,200).get("totalElements").asInt());
        b.request("PUT","/finance/entries/"+id,input,200); // unchanged archived links remain editable
        b.request("POST","/finance/entries",input,400); // archived records cannot receive new links
        b.request("POST","/finance/entries/"+id+"/pay",Map.of("paymentDate",today),200);
        b.request("PUT","/finance/entries/"+id,input,400);
        amount("675.50",b.summary().get("balance"));
        b.request("POST","/finance/entries/"+id+"/cancel",null,200);
        b.request("PUT","/finance/entries/"+id,input,400);
        amount("500",b.summary().get("balance"));
    }
    @Test void directoryEnforcesOwnershipKindsDuplicatesAndArchive() throws Exception {
        var a=new Browser().signup();var b=new Browser().signup();String bank=a.account("Conta","0"),other=b.account("Conta","0");
        String person=a.request("POST","/contacts",contact("Fornecedor","SUPPLIER","11111111111",true),201).get("id").asText();
        a.request("POST","/contacts",contact("Duplicado","SUPPLIER","11111111111",true),409);
        b.request("POST","/contacts",contact("Mesmo documento em outra empresa","CUSTOMER","11111111111",true),201);
        a.request("POST","/contacts",contact("Inválido","CUSTOMER","123",true),400);
        String category=a.request("POST","/categories",Map.of("name","Operação","kind","EXPENSE"),201).get("id").asText();
        a.request("POST","/categories",Map.of("name","  OPERAÇÃO  ","kind","EXPENSE"),409);
        b.request("PUT","/contacts/"+person,contact("Invasão","SUPPLIER","11111111111",true),404);
        b.request("PUT","/categories/"+category,Map.of("name","Invasão","kind","EXPENSE"),404);
        assertEquals(0,b.request("GET","/categories",null,200).size());
        var bad=b.entry(other,false,"20","EXPENSE");bad.put("categoryId",category);
        b.request("POST","/finance/entries",bad,404);bad.remove("categoryId");bad.put("contactId",person);
        b.request("POST","/finance/entries",bad,404);
        var own=a.entry(bank,false,"20","INCOME");own.put("contactId",person);
        a.request("POST","/finance/entries",own,400);own.remove("contactId");own.put("categoryId",category);
        a.request("POST","/finance/entries",own,400);
        own.put("type","EXPENSE");own.put("contactId",person);
        String id=a.request("POST","/finance/entries",own,201).get("id").asText();
        b.request("PUT","/finance/entries/"+id,b.entry(other,false,"1","INCOME"),404);
        a.request("PUT","/contacts/"+person,contact("Fornecedor","CUSTOMER","11111111111",true),400);
        a.request("PUT","/categories/"+category,Map.of("name","Operação","kind","INCOME"),400);
        own.put("paid",true);a.request("PUT","/finance/entries/"+id,own,400);
        own.put("paid",false);own.put("accountId",other);a.request("PUT","/finance/entries/"+id,own,404);
        String both=a.request("POST","/contacts",contact("Ambos","BOTH","",true),201).get("id").asText();
        var flexible=a.entry(bank,false,"10","INCOME");flexible.put("contactId",both);
        a.request("POST","/finance/entries",flexible,201);flexible.put("type","EXPENSE");
        a.request("POST","/finance/entries",flexible,201);
    }

    Map<String,Object> seriesInput(Browser b,String account,String kind,String value,int count) {
        var input=b.entry(account,false,value,"EXPENSE");input.put("dueDate","2027-01-31");
        return new HashMap<>(Map.of("requestId",UUID.randomUUID().toString(),"kind",kind,"occurrences",count,"entry",input));
    }
    @Test void installmentsKeepEveryCentDatesAndPaymentsWhenStopped() throws Exception {
        var b=new Browser().signup();String account=b.account("Banco","1000");
        var input=seriesInput(b,account,"INSTALLMENT","100",3);
        var created=b.request("POST","/finance/series",input,201);String id=created.get("id").asText();
        amount("100",created.get("totalAmount"));assertEquals(3,created.get("pending").asInt());
        assertEquals("2027-03-31",created.get("lastDueDate").asText());
        b.request("POST","/finance/series",input,200);
        var rows=b.request("GET","/finance/entries?seriesId="+id,null,200).get("content");
        assertEquals(3,rows.size());
        var ordered=new ArrayList<JsonNode>();rows.forEach(ordered::add);ordered.sort(Comparator.comparingInt(e->e.get("sequenceNumber").asInt()));
        amount("33.34",ordered.get(0).get("amount"));amount("33.33",ordered.get(1).get("amount"));amount("33.33",ordered.get(2).get("amount"));
        assertEquals("2027-02-28",ordered.get(1).get("dueDate").asText());
        String first=ordered.get(0).get("id").asText();b.request("POST","/finance/entries/"+first+"/pay",Map.of("paymentDate",today),200);
        amount("966.66",b.summary().get("balance"));
        b.request("PUT","/finance/entries/"+ordered.get(1).get("id").asText(),b.entry(account,false,"40","EXPENSE"),400);
        var stopped=b.request("POST","/finance/series/"+id+"/stop",null,200);
        assertEquals(1,stopped.get("paid").asInt());assertEquals(2,stopped.get("cancelled").asInt());assertEquals(0,stopped.get("pending").asInt());
        b.request("POST","/finance/series/"+id+"/stop",null,200);amount("966.66",b.summary().get("balance"));
        input.put("occurrences",4);b.request("POST","/finance/series",input,409);
    }
    @Test void recurringSeriesAreAtomicScopedAndSafeToRetryConcurrently() throws Exception {
        var a=new Browser().signup();var b=new Browser().signup();String account=a.account("Conta","0");
        var input=seriesInput(a,account,"RECURRING","250",4);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks=List.<java.util.concurrent.Callable<Integer>>of(
                ()->a.call("POST","/finance/series",input,true).statusCode(),()->a.call("POST","/finance/series",input,true).statusCode());
            var statuses=new ArrayList<Integer>();for(var result:pool.invokeAll(tasks))statuses.add(result.get());
            Collections.sort(statuses);assertEquals(List.of(200,201),statuses);
        }finally{pool.shutdownNow();}
        var list=a.request("GET","/finance/series",null,200);assertEquals(1,list.get("totalElements").asInt());
        var item=list.get("content").get(0);String id=item.get("id").asText();amount("1000",item.get("totalAmount"));
        var rows=a.request("GET","/finance/entries?seriesId="+id,null,200).get("content");assertEquals(4,rows.size());
        for(var row:rows)amount("250",row.get("amount"));
        assertEquals(0,b.request("GET","/finance/series",null,200).get("totalElements").asInt());
        assertEquals(0,b.request("GET","/finance/entries?seriesId="+id,null,200).get("totalElements").asInt());
        b.request("POST","/finance/series/"+id+"/stop",null,404);
        b.request("POST","/finance/series",input,404);
        assertEquals(0,b.request("GET","/finance/series",null,200).get("totalElements").asInt());
        a.request("POST","/finance/series",seriesInput(a,account,"INSTALLMENT","0.02",3),400);
        a.request("POST","/finance/series",seriesInput(a,account,"RECURRING","1",121),400);
        assertEquals(1,a.request("GET","/finance/series",null,200).get("totalElements").asInt());
    }
    @Test void seriesOnlySettlesFirstOccurrenceAndExposesVersion() throws Exception {
        var b=new Browser().signup();var account=b.account("Conta","500");
        var input=seriesInput(b,account,"RECURRING","50",3);
        var template=b.entry(account,true,"50","EXPENSE");input.put("entry",template);
        var item=b.request("POST","/finance/series",input,201);
        assertEquals(1,item.get("paid").asInt());assertEquals(2,item.get("pending").asInt());amount("450",b.summary().get("balance"));
        assertTrue(b.request("GET","/app",null,200).get("version").asText().matches("[0-9]+[.][0-9]+[.][0-9]+"));
    }

    String reportQuery(String start,String end) { return "/reports?start="+start+"&end="+end; }
    @Test void reportsSeparatePaymentDueAndExcludeTransfersAndCancelledAmounts() throws Exception {
        var b=new Browser().signup();var account=b.account("Banco principal","1000");var cash=b.account("Caixa","100");
        String person=b.request("POST","/contacts",contact("Cliente Alfa","CUSTOMER","",true),201).get("id").asText();
        var income=b.entry(account,true,"500","INCOME");income.put("dueDate",LocalDate.parse(today).minusDays(10).toString());income.put("contactId",person);
        b.request("POST","/finance/entries",income,201);
        var expense=b.entry(account,true,"200","EXPENSE");expense.put("dueDate",LocalDate.parse(today).minusDays(40).toString());expense.put("category","Operação");b.request("POST","/finance/entries",expense,201);
        var pending=b.entry(account,false,"300","INCOME");pending.put("dueDate",LocalDate.parse(today).plusDays(1).toString());b.request("POST","/finance/entries",pending,201);
        pending=b.entry(account,false,"50","EXPENSE");pending.put("dueDate",LocalDate.parse(today).minusDays(1).toString());b.request("POST","/finance/entries",pending,201);
        String cancelled=b.request("POST","/finance/entries",b.entry(account,true,"400","EXPENSE"),201).get("id").asText();b.request("POST","/finance/entries/"+cancelled+"/cancel",null,200);
        b.request("POST","/finance/transfers",Map.of("sourceId",account,"destinationId",cash,"amount","100","date",today,"description","Transferência interna"),201);
        var paid=b.request("GET",reportQuery(today,today),null,200);assertEquals(2,paid.get("totalElements").asInt());
        amount("500",paid.get("totals").get("income"));amount("200",paid.get("totals").get("expense"));amount("300",paid.get("totals").get("result"));
        amount("200",paid.get("categories").get(0).get("amount"));assertEquals("Operação",paid.get("categories").get(0).get("category").asText());
        var due=b.request("GET",reportQuery(LocalDate.parse(today).minusDays(1).toString(),LocalDate.parse(today).plusDays(1).toString())+"&basis=DUE&status=PENDING",null,200);
        assertEquals(2,due.get("totalElements").asInt());amount("300",due.get("totals").get("pendingIncome"));amount("50",due.get("totals").get("pendingExpense"));amount("0",due.get("totals").get("income"));
        var cancelledReport=b.request("GET",reportQuery(today,today)+"&basis=DUE&status=CANCELLED",null,200);assertEquals(1,cancelledReport.get("totalElements").asInt());amount("0",cancelledReport.get("totals").get("expense"));
        assertEquals(1,b.request("GET",reportQuery(today,today)+"&type=EXPENSE&category=Opera&accountId="+account,null,200).get("totalElements").asInt());
        assertEquals(1,b.request("GET",reportQuery(today,today)+"&contact=Alfa",null,200).get("totalElements").asInt());
        b.request("GET",reportQuery(today,today)+"&status=PENDING",null,400);
        b.request("GET",reportQuery(today,LocalDate.parse(today).minusDays(1).toString()),null,400);
        var other=new Browser().signup();assertEquals(0,other.request("GET",reportQuery(today,today),null,200).get("totalElements").asInt());
        other.request("GET",reportQuery(today,today)+"&accountId="+account,null,404);
        other.request("GET","/reports/forecast?accountId="+account,null,404);
        other.request("GET","/reports/export?start="+today+"&end="+today+"&accountId="+account+"&format=csv",null,404);
    }
    @Test void forecastStartsAtCurrentBalanceAndHandlesOverdueWithoutSettlingEntries() throws Exception {
        var b=new Browser().signup();var bank=b.account("Banco","1000");var cash=b.account("Caixa","200");
        b.request("POST","/finance/entries",b.entry(bank,true,"100","INCOME"),201);b.request("POST","/finance/entries",b.entry(bank,true,"50","EXPENSE"),201);
        b.request("POST","/finance/transfers",Map.of("sourceId",bank,"destinationId",cash,"amount","100","date",today,"description","Transferência"),201);
        for(var item:List.of(new String[]{"1500","EXPENSE","1"},new String[]{"300","INCOME","2"},new String[]{"80","INCOME","-1"},new String[]{"20","EXPENSE","-2"},new String[]{"999","EXPENSE","40"})) {
            var input=b.entry(bank,false,item[0],item[1]);input.put("dueDate",LocalDate.parse(today).plusDays(Integer.parseInt(item[2])).toString());b.request("POST","/finance/entries",input,201);
        }
        var forecast=b.request("GET","/reports/forecast?days=30",null,200);
        amount("1250",forecast.get("openingBalance"));amount("50",forecast.get("closingBalance"));amount("-250",forecast.get("minimumBalance"));
        assertEquals(LocalDate.parse(today).plusDays(1).toString(),forecast.get("firstNegativeDate").asText());assertEquals(31,forecast.get("days").size());
        amount("80",forecast.get("overdueIncome"));amount("20",forecast.get("overdueExpense"));
        forecast=b.request("GET","/reports/forecast?days=30&includeOverdue=true",null,200);amount("110",forecast.get("closingBalance"));amount("-190",forecast.get("minimumBalance"));amount("380",forecast.get("pendingIncome"));
        amount("300",b.request("GET","/reports/forecast?days=30&accountId="+cash,null,200).get("closingBalance"));
        amount("1250",b.summary().get("balance"));b.request("GET","/reports/forecast?days=0",null,400);b.request("GET","/reports/forecast?days=367",null,400);
    }
    @Test void exportsAreRealFilesAndProtectSpreadsheetText() throws Exception {
        var b=new Browser().signup();var account=b.account("Banco São José","0");
        var row=b.entry(account,true,"123.45","INCOME");row.put("description","=SUM(1,2) São José & Filhos <teste> 🎮");b.request("POST","/finance/entries",row,201);
        String base="http://localhost:"+port+"/api/v1/reports/export?start="+today+"&end="+today+"&format=";
        var csv=b.client.send(HttpRequest.newBuilder(URI.create(base+"csv")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,csv.statusCode());
        String csvText=new String(csv.body(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(csvText.startsWith("\ufeff"));assertTrue(csvText.contains("'=SUM(1,2)"));assertTrue(csvText.contains("123,45"));assertTrue(csv.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        var xlsx=b.client.send(HttpRequest.newBuilder(URI.create(base+"xlsx")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,xlsx.statusCode());
        var xmls=new HashMap<String,String>();try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(xlsx.body()))) {java.util.zip.ZipEntry part;while((part=zip.getNextEntry())!=null)xmls.put(part.getName(),new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        String sheet=xmls.get("xl/worksheets/sheet1.xml");assertNotNull(sheet);assertTrue(sheet.contains("<v>123.45</v>"));assertTrue(sheet.contains("t=\"inlineStr\""));assertFalse(sheet.contains("<f>"));assertTrue(sheet.contains("&amp; Filhos &lt;teste&gt;"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING,true);
        for(var content:xmls.values())factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var pdf=b.client.send(HttpRequest.newBuilder(URI.create(base+"pdf")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,pdf.statusCode());
        try(var document=org.apache.pdfbox.Loader.loadPDF(pdf.body())){assertEquals(1,document.getNumberOfPages());String text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);assertTrue(text.contains("São José"));assertTrue(text.contains("123,45"));}
        for(int i=0;i<25;i++)b.request("POST","/finance/entries",b.entry(account,true,"1","EXPENSE"),201);
        pdf=b.client.send(HttpRequest.newBuilder(URI.create(base+"pdf")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());try(var document=org.apache.pdfbox.Loader.loadPDF(pdf.body())){assertTrue(document.getNumberOfPages()>1);}
        b.request("GET","/reports/export?start="+today+"&end="+today+"&format=html",null,400);
    }
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test void reportTotalsCoverAllPagesAndExportRequiresRefiningLargeSelections() throws Exception {
        var b=new Browser().signup();var account=b.account("Conta","0");String company=b.request("GET","/auth/me",null,200).get("companyId").asText();
        var batch=new ArrayList<Object[]>();for(int i=0;i<5001;i++)batch.add(new Object[]{UUID.randomUUID(),UUID.fromString(company),UUID.fromString(account),"Registro de volume",LocalDate.parse(today),LocalDate.parse(today)});
        jdbc.batchUpdate("INSERT INTO financial_entries(id,company_id,account_id,description,category,type,amount,paid_amount,due_date,payment_date,status,created_at,updated_at) VALUES(?,?,?,?,'Teste','INCOME',1,1,?,?,'PAID',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",batch);
        jdbc.batchUpdate("INSERT INTO entry_payments(id,company_id,entry_id,account_id,amount,payment_date,created_at,updated_at) VALUES(?,?,?,?,1,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",batch.stream().map(row->new Object[]{row[0],row[1],row[0],row[2],row[5]}).toList());
        var report=b.request("GET",reportQuery(today,today),null,200);assertEquals(5001,report.get("totalElements").asInt());assertEquals(20,report.get("content").size());amount("5001",report.get("totals").get("income"));
        b.request("GET","/reports/export?start="+today+"&end="+today+"&format=csv",null,400);
    }


    byte[] imageFile(String format) throws Exception {
        var image=new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out=new java.io.ByteArrayOutputStream();assertTrue(javax.imageio.ImageIO.write(image,format,out));return out.toByteArray();
    }
    byte[] pdfFile(boolean encrypted) throws Exception {
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            if(encrypted)pdf.protect(new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy("owner-password","reader-password",new org.apache.pdfbox.pdmodel.encryption.AccessPermission()));
            pdf.save(out);return out.toByteArray();
        }
    }
    HttpResponse<byte[]> upload(Browser b,String entry,String filename,byte[] data,boolean withCsrf) throws Exception {
        String boundary="fintry-"+UUID.randomUUID();var out=new java.io.ByteArrayOutputStream();
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));out.write(data);out.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/finance/entries/"+entry+"/attachments")).header("Content-Type","multipart/form-data; boundary="+boundary);
        if(withCsrf)request.header(b.header,b.token);return b.client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    JsonNode uploaded(Browser b,String entry,String name,byte[] data) throws Exception {
        var response=upload(b,entry,name,data,true);assertEquals(201,response.statusCode(),new String(response.body(),java.nio.charset.StandardCharsets.UTF_8));return json.readTree(response.body());
    }
    @Test void attachmentsRemainThroughPaymentCancellationAndDownloadExactBytes() throws Exception {
        var b=new Browser().signup();var account=b.account("Banco principal","100");
        String entry=b.request("POST","/finance/entries",b.entry(account,false,"10","EXPENSE"),201).get("id").asText();
        var details=b.request("GET","/finance/entries/"+entry,null,200);assertEquals("Banco principal",details.get("accountName").asText());assertNotNull(details.get("createdAt"));
        byte[] png=imageFile("PNG");var file=uploaded(b,entry,"recibo São José.png",png);assertEquals("recibo São José.png",file.get("filename").asText());assertEquals(png.length,file.get("byteCount").asInt());assertEquals("image/png",file.get("contentType").asText());assertFalse(file.has("content"));
        uploaded(b,entry,"nota.pdf",pdfFile(false));uploaded(b,entry,"foto.jpeg",imageFile("JPEG"));
        String route="/finance/entries/"+entry+"/attachments";var files=b.request("GET",route,null,200);assertEquals(3,files.size());assertFalse(files.get(0).has("content"));assertEquals("Usuário teste",files.get(0).get("uploadedByName").asText());
        var download=b.client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+route+"/"+file.get("id").asText())).GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,download.statusCode());assertArrayEquals(png,download.body());assertTrue(download.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment;"));assertTrue(download.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        amount("100",b.summary().get("balance"));b.request("POST","/finance/entries/"+entry+"/pay",Map.of("paymentDate",today),200);amount("90",b.summary().get("balance"));assertEquals(3,b.request("GET",route,null,200).size());
        b.request("POST","/finance/entries/"+entry+"/cancel",null,200);assertEquals("CANCELLED",b.request("GET","/finance/entries/"+entry,null,200).get("entry").get("status").asText());assertEquals(3,b.request("GET",route,null,200).size());amount("100",b.summary().get("balance"));
        b.request("DELETE",route+"/"+file.get("id").asText(),null,204);assertEquals(2,b.request("GET",route,null,200).size());b.request("GET",route+"/"+file.get("id").asText(),null,404);amount("100",b.summary().get("balance"));
    }
    @Test void attachmentsAndEntryDetailsRespectCompanyEntryAndCsrfBoundaries() throws Exception {
        var a=new Browser().signup();var b=new Browser().signup();var account=a.account("Conta privada","0");
        String entry=a.request("POST","/finance/entries",a.entry(account,false,"5","INCOME"),201).get("id").asText();String second=a.request("POST","/finance/entries",a.entry(account,false,"6","INCOME"),201).get("id").asText();
        byte[] png=imageFile("PNG");var file=uploaded(a,entry,"../../recibo.png",png);assertEquals("recibo.png",file.get("filename").asText());String route="/finance/entries/"+entry+"/attachments";
        assertEquals(403,upload(a,entry,"arquivo.png",png,false).statusCode());
        b.request("GET","/finance/entries/"+entry,null,404);b.request("GET",route,null,404);b.request("GET",route+"/"+file.get("id").asText(),null,404);b.request("DELETE",route+"/"+file.get("id").asText(),null,404);assertEquals(404,upload(b,entry,"privado.png",png,true).statusCode());
        a.request("GET","/finance/entries/"+second+"/attachments/"+file.get("id").asText(),null,404);a.request("DELETE","/finance/entries/"+second+"/attachments/"+file.get("id").asText(),null,404);
        var anonymous=new Browser();anonymous.request("GET",route,null,401);anonymous.request("GET",route+"/"+file.get("id").asText(),null,401);assertEquals(1,a.request("GET",route,null,200).size());
    }
    @Test void attachmentValidationRejectsDisguisedCorruptEncryptedAndLargeFiles() throws Exception {
        var b=new Browser().signup();var account=b.account("Conta","0");String entry=b.request("POST","/finance/entries",b.entry(account,false,"1","INCOME"),201).get("id").asText();
        byte[] png=imageFile("PNG");assertEquals(400,upload(b,entry,"arquivo.svg","<svg/>".getBytes(),true).statusCode());assertEquals(400,upload(b,entry,"falso.pdf",png,true).statusCode());assertEquals(400,upload(b,entry,"falso.png",imageFile("JPEG"),true).statusCode());assertEquals(400,upload(b,entry,"vazio.png",new byte[0],true).statusCode());assertEquals(400,upload(b,entry,"quebrado.pdf","%PDF-1.7 inválido".getBytes(),true).statusCode());assertEquals(400,upload(b,entry,"segredo.pdf",pdfFile(true),true).statusCode());
        var large=upload(b,entry,"grande.png",new byte[5*1024*1024+1],true);assertEquals(413,large.statusCode());assertTrue(new String(large.body(),java.nio.charset.StandardCharsets.UTF_8).contains("5 MB"));assertEquals(0,b.request("GET","/finance/entries/"+entry+"/attachments",null,200).size());
    }
    @Test void concurrentAttachmentsCannotExceedTenAndRemovalFreesOneSlot() throws Exception {
        var b=new Browser().signup();var account=b.account("Conta","0");String entry=b.request("POST","/finance/entries",b.entry(account,false,"1","INCOME"),201).get("id").asText();byte[] png=imageFile("PNG");
        for(int i=0;i<9;i++)uploaded(b,entry,"recibo-"+i+".png",png);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var results=pool.invokeAll(List.<java.util.concurrent.Callable<Integer>>of(()->upload(b,entry,"concorrente-a.png",png,true).statusCode(),()->upload(b,entry,"concorrente-b.png",png,true).statusCode()));
            var codes=results.stream().map(result->{try{return result.get();}catch(Exception ex){throw new RuntimeException(ex);}}).sorted().toList();assertEquals(List.of(201,400),codes);
        }finally{pool.shutdownNow();}
        String route="/finance/entries/"+entry+"/attachments";var files=b.request("GET",route,null,200);assertEquals(10,files.size());b.request("DELETE",route+"/"+files.get(0).get("id").asText(),null,204);uploaded(b,entry,"substituto.png",png);assertEquals(10,b.request("GET",route,null,200).size());
    }

    @Test void cashLifecycleAndAtomicTransfer() throws Exception {
        var b=new Browser().signup();String bank=b.account("Banco","1000.00"),cash=b.account("Caixa","0.00");
        var entry=b.request("POST","/finance/entries",b.entry(bank,false,"100.00","INCOME"),201);
        amount("1000",b.summary().get("balance"));amount("100",b.summary().get("pendingIncome"));
        String id=entry.get("id").asText();
        b.request("POST","/finance/entries/"+id+"/pay",Map.of("paymentDate",today),200);
        b.request("POST","/finance/entries/"+id+"/pay",Map.of("paymentDate",today),200);
        amount("1100",b.summary().get("balance"));amount("0",b.summary().get("pendingIncome"));
        var expense=b.request("POST","/finance/entries",b.entry(bank,true,"50.00","EXPENSE"),201);
        amount("1050",b.summary().get("balance"));
        String expenseId=expense.get("id").asText();
        b.request("POST","/finance/entries/"+expenseId+"/cancel",null,200);
        b.request("POST","/finance/entries/"+expenseId+"/cancel",null,200);
        b.request("POST","/finance/entries/"+expenseId+"/pay",Map.of("paymentDate",today),400);
        b.request("POST","/finance/transfers",Map.of("sourceId",bank,"destinationId",cash,"amount","200.00","date",today,"description","Transferência"),201);
        var summary=b.summary();amount("1100",summary.get("balance"));amount("100",summary.get("income"));
        amount("0",summary.get("expense"));amount("100",summary.get("result"));
        var accounts=b.request("GET","/finance/accounts",null,200);
        for(var a:accounts)amount(a.get("id").asText().equals(bank)?"900":"200",a.get("balance"));
        var rows=b.request("GET","/finance/entries?size=100",null,200).get("content");
        for(var row:rows)if(!row.get("transferId").isNull())
            b.request("POST","/finance/entries/"+row.get("id").asText()+"/cancel",null,400);
    }
    @Test void partialPaymentsTrackHistoryRemainingBalanceAndCashReports() throws Exception {
        var b=new Browser().signup();String account=b.account("Banco","0");
        String id=b.request("POST","/finance/entries",b.entry(account,false,"100.00","INCOME"),201).get("id").asText();
        var first=b.request("POST","/finance/entries/"+id+"/pay",Map.of("paymentDate",today,"amount","40.00"),200);
        assertEquals("PARTIAL",first.get("status").asText());amount("40",first.get("receivedAmount"));amount("60",first.get("remainingAmount"));
        amount("40",b.summary().get("balance"));amount("40",b.summary().get("income"));amount("60",b.summary().get("pendingIncome"));
        var dueReport=b.request("GET",reportQuery(today,today)+"&basis=DUE&status=PARTIAL",null,200);amount("40",dueReport.get("totals").get("income"));amount("60",dueReport.get("totals").get("pendingIncome"));
        var forecast=b.request("GET","/reports/forecast?days=30&includeOverdue=true",null,200);amount("60",forecast.get("pendingIncome"));amount("100",forecast.get("closingBalance"));
        var details=b.request("GET","/finance/entries/"+id,null,200);assertEquals(1,details.get("payments").size());amount("40",details.get("payments").get(0).get("amount"));
        b.request("POST","/finance/entries/"+id+"/pay",Map.of("paymentDate",today,"amount","60.01"),400);
        var paid=b.request("POST","/finance/entries/"+id+"/pay",Map.of("paymentDate",today,"amount","60.00"),200);
        assertEquals("PAID",paid.get("status").asText());amount("0",paid.get("remainingAmount"));amount("100",b.summary().get("balance"));amount("0",b.summary().get("pendingIncome"));
        details=b.request("GET","/finance/entries/"+id,null,200);assertEquals(2,details.get("payments").size());
        var report=b.request("GET",reportQuery(today,today),null,200);assertEquals(2,report.get("totalElements").asInt());amount("100",report.get("totals").get("income"));
    }
    @Test void companiesCannotReadOrMutateEachOthersData() throws Exception {
        var a=new Browser().signup();var b=new Browser().signup();String account=a.account("Privada","750");
        var entry=a.request("POST","/finance/entries",a.entry(account,false,"30","INCOME"),201).get("id").asText();
        assertEquals(0,b.request("GET","/finance/accounts",null,200).size());
        assertEquals(0,b.request("GET","/finance/entries",null,200).get("totalElements").asInt());
        amount("0",b.summary().get("balance"));
        b.request("POST","/finance/entries",b.entry(account,true,"100","INCOME"),404);
        b.request("POST","/finance/entries/"+entry+"/pay",Map.of("paymentDate",today),404);
        b.request("POST","/finance/entries/"+entry+"/cancel",null,404);
        var own=b.account("Própria","0");
        b.request("POST","/finance/transfers",Map.of("sourceId",own,"destinationId",account,"amount","15","date",today,"description","Inválida"),404);
        amount("0",b.summary().get("balance"));
        b.request("GET","/companies",null,404); // global company enumeration removed
        var company=a.request("GET","/companies/me",null,200).get("id").asText();
        b.request("GET","/companies/"+company,null,404);
    }
    @Test void validatesAmountsDatesFiltersAndCsrf() throws Exception {
        var anon=new Browser();anon.request("GET","/finance/accounts",null,401);
        var b=new Browser().signup();var account=b.account("Conta","0");
        assertEquals(403,b.call("POST","/finance/accounts",Map.of("name","Sem CSRF","initialBalance","0"),false).statusCode());
        for(var value:List.of("-1","0","0.001","1000000000000"))
            b.request("POST","/finance/entries",b.entry(account,true,value,"INCOME"),400);
        var future=b.entry(account,true,"100","INCOME");future.put("paymentDate",LocalDate.parse(today).plusDays(1).toString());
        b.request("POST","/finance/entries",future,400);
        b.request("GET","/finance/summary?month=errado",null,400);
        b.request("GET","/finance/entries?size=101",null,400);
        b.request("GET","/finance/entries?start=2026-10-20&end=2026-10-19",null,400);
        b.request("POST","/finance/entries",b.entry(account,false,"80","INCOME"),201);
        b.request("POST","/finance/entries",b.entry(account,false,"20","EXPENSE"),201);
        assertEquals(1,b.request("GET","/finance/entries?type=EXPENSE&status=PENDING",null,200).get("totalElements").asInt());
        b.request("POST","/finance/accounts",Map.of("name","Conta","initialBalance","0"),409);
        b.request("POST","/auth/logout",null,204);b.request("GET","/finance/accounts",null,401);
    }
    @Test void concurrentPaymentIsAppliedOnceAndSessionSurvivesReload() throws Exception {
        var b=new Browser().signup();var account=b.account("Concorrência","0");
        var id=b.request("POST","/finance/entries",b.entry(account,false,"99.90","INCOME"),201).get("id").asText();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks=List.<java.util.concurrent.Callable<Integer>>of(
                ()->b.call("POST","/finance/entries/"+id+"/pay",Map.of("paymentDate",today),true).statusCode(),
                ()->b.call("POST","/finance/entries/"+id+"/pay",Map.of("paymentDate",today),true).statusCode());
            for(var result:pool.invokeAll(tasks))assertEquals(200,result.get());
        } finally { pool.shutdownNow(); }
        amount("99.90",b.summary().get("balance"));
        assertNotNull(b.request("GET","/auth/me",null,200).get("companyId"));
    }
    @Test void sampleIsExplicitAndCannotBeAddedTwice() throws Exception {
        var b=new Browser().signup();b.request("POST","/finance/sample",null,201);
        assertEquals(4,b.request("GET","/finance/accounts",null,200).size());
        assertTrue(b.summary().get("income").decimalValue().signum()>0);
        b.request("POST","/finance/sample",null,201);
        assertEquals(4,b.request("GET","/finance/accounts",null,200).size());
    }
}
