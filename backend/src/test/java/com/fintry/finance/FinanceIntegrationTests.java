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
        assertEquals(2,b.request("GET","/finance/accounts",null,200).size());
        assertTrue(b.summary().get("income").decimalValue().signum()>0);
        b.request("POST","/finance/sample",null,400);
    }
}
