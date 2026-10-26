package com.fintry.report;

import com.fintry.auth.CurrentUser;
import com.fintry.company.repository.CompanyRepository;
import com.fintry.finance.*;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.fintry.finance.FinancialEntry.Type.*;
import static com.fintry.finance.FinancialEntry.Status.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ReportService {
    public enum Basis { PAYMENT, DUE }
    public record Filters(LocalDate start,LocalDate end,Basis basis,FinancialEntry.Status status,
        FinancialEntry.Type type,UUID accountId,String category,String contact) {}
    public record Totals(BigDecimal income,BigDecimal expense,BigDecimal result,BigDecimal pendingIncome,BigDecimal pendingExpense) {}
    public record CategoryTotal(String category,BigDecimal amount) {}
    public record Row(UUID id,String description,String category,String contactName,String accountName,
        FinancialEntry.Type type,FinancialEntry.Status status,BigDecimal amount,LocalDate dueDate,LocalDate paymentDate,Integer sequenceNumber,Integer seriesCount,UUID paymentId) {}
    public record Report(String companyName,Filters filters,Totals totals,List<CategoryTotal> categories,
        List<Row> content,long totalElements,int totalPages,int number) {}
    public record Export(String companyName,Filters filters,Totals totals,List<Row> rows,String accountName) {}
    public record Day(LocalDate date,BigDecimal income,BigDecimal expense,BigDecimal balance) {}
    public record Forecast(LocalDate start,LocalDate end,BigDecimal openingBalance,BigDecimal closingBalance,
        BigDecimal pendingIncome,BigDecimal pendingExpense,BigDecimal overdueIncome,BigDecimal overdueExpense,
        BigDecimal minimumBalance,LocalDate firstNegativeDate,boolean includeOverdue,List<Day> days) {}
    private final CurrentUser current;
    private final AccountRepository accounts;
    private final CompanyRepository companies;
    private final EntryRepository entries;
    private final EntryPaymentRepository payments;
    private final FinanceService finance;
    private final EntityManager em;

    private ResponseStatusException invalid(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private void validate(Filters f) {
        if(f.start()==null || f.end()==null || f.basis()==null || f.start().isAfter(f.end()) || ChronoUnit.DAYS.between(f.start(),f.end())>=366)
            throw invalid("Escolha um período válido de até 366 dias.");
        if(f.basis()==Basis.PAYMENT && f.status()!=null && f.status()!=PAID)
            throw invalid("Por pagamento, o relatório considera apenas lançamentos realizados. Use vencimento para pendências.");
        if((f.category()!=null && f.category().length()>80) || (f.contact()!=null && f.contact().length()>120))throw invalid("Filtro de texto muito longo.");
        if(f.accountId()!=null && accounts.findByIdAndCompanyId(f.accountId(),current.companyId()).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Conta não encontrada.");
    }
    private String contains(String value){return "%"+value.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private Specification<FinancialEntry> filter(Filters f) {
        UUID company=current.companyId();
        return (root,query,cb)->{
            var p=new ArrayList<Predicate>();p.add(cb.equal(root.get("companyId"),company));
            p.add(cb.isNull(root.get("transferId")));
            p.add(cb.between(root.get(f.basis()==Basis.PAYMENT?"paymentDate":"dueDate"),f.start(),f.end()));
            if(f.basis()==Basis.PAYMENT)p.add(cb.equal(root.get("status"),PAID));
            else if(f.status()!=null)p.add(cb.equal(root.get("status"),f.status()));
            else p.add(cb.notEqual(root.get("status"),CANCELLED));
            if(f.type()!=null)p.add(cb.equal(root.get("type"),f.type()));
            if(f.accountId()!=null)p.add(cb.equal(root.get("accountId"),f.accountId()));
            if(f.category()!=null && !f.category().isBlank())p.add(cb.like(cb.lower(root.get("category")),contains(f.category()),'!'));
            if(f.contact()!=null && !f.contact().isBlank())p.add(cb.like(cb.lower(root.get("contactName")),contains(f.contact()),'!'));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
    private Specification<EntryPayment> paymentFilter(Filters f) {
        UUID company=current.companyId();
        return (root,query,cb)->{
            var entry=root.join("entry");var p=new ArrayList<Predicate>();
            p.add(cb.equal(root.get("companyId"),company));
            p.add(cb.between(root.get("paymentDate"),f.start(),f.end()));
            p.add(cb.notEqual(entry.get("status"),CANCELLED));p.add(cb.isNull(entry.get("transferId")));
            if(f.type()!=null)p.add(cb.equal(entry.get("type"),f.type()));
            if(f.accountId()!=null)p.add(cb.equal(entry.get("accountId"),f.accountId()));
            if(f.category()!=null&&!f.category().isBlank())p.add(cb.like(cb.lower(entry.get("category")),contains(f.category()),'!'));
            if(f.contact()!=null&&!f.contact().isBlank())p.add(cb.like(cb.lower(entry.get("contactName")),contains(f.contact()),'!'));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
    private BigDecimal zero(Object value){return value==null?BigDecimal.ZERO:(BigDecimal)value;}
    private Expression<BigDecimal> sum(CriteriaBuilder cb,Root<FinancialEntry> root,FinancialEntry.Status status,FinancialEntry.Type type) {
        return cb.sum(cb.<BigDecimal>selectCase().when(cb.and(cb.equal(root.get("status"),status),cb.equal(root.get("type"),type)),root.<BigDecimal>get("amount")).otherwise(BigDecimal.ZERO));
    }
    private Totals totals(Specification<FinancialEntry> spec) {
        var cb=em.getCriteriaBuilder();var query=cb.createTupleQuery();var root=query.from(FinancialEntry.class);
        var paidIncome=cb.sum(cb.<BigDecimal>selectCase().when(cb.and(cb.notEqual(root.get("status"),CANCELLED),cb.equal(root.get("type"),INCOME)),root.<BigDecimal>get("paidAmount")).otherwise(BigDecimal.ZERO));
        var paidExpense=cb.sum(cb.<BigDecimal>selectCase().when(cb.and(cb.notEqual(root.get("status"),CANCELLED),cb.equal(root.get("type"),EXPENSE)),root.<BigDecimal>get("paidAmount")).otherwise(BigDecimal.ZERO));
        var remaining=cb.diff(root.<BigDecimal>get("amount"),root.<BigDecimal>get("paidAmount"));
        var pendingIncome=cb.sum(cb.<BigDecimal>selectCase().when(cb.and(cb.notEqual(root.get("status"),CANCELLED),cb.equal(root.get("type"),INCOME)),remaining).otherwise(BigDecimal.ZERO));
        var pendingExpense=cb.sum(cb.<BigDecimal>selectCase().when(cb.and(cb.notEqual(root.get("status"),CANCELLED),cb.equal(root.get("type"),EXPENSE)),remaining).otherwise(BigDecimal.ZERO));
        query.multiselect(paidIncome,paidExpense,pendingIncome,pendingExpense);
        query.where(spec.toPredicate(root,query,cb));var row=em.createQuery(query).getSingleResult();
        var income=zero(row.get(0));var expense=zero(row.get(1));return new Totals(income,expense,income.subtract(expense),zero(row.get(2)),zero(row.get(3)));
    }
    private List<CategoryTotal> categories(Specification<FinancialEntry> spec) {
        var cb=em.getCriteriaBuilder();var query=cb.createTupleQuery();var root=query.from(FinancialEntry.class);var total=cb.sum(root.<BigDecimal>get("paidAmount"));
        query.multiselect(root.get("category"),total).where(cb.and(spec.toPredicate(root,query,cb),cb.notEqual(root.get("status"),CANCELLED),cb.equal(root.get("type"),EXPENSE)))
            .groupBy(root.get("category")).orderBy(cb.desc(total),cb.asc(root.get("category")));
        return em.createQuery(query).getResultList().stream().map(row->new CategoryTotal((String)row.get(0),zero(row.get(1)))).toList();
    }
    private Map<UUID,String> accountNames() {
        var names=new HashMap<UUID,String>();for(var a:accounts.findByCompanyIdOrderByNameAsc(current.companyId()))names.put(a.getId(),a.getName());return names;
    }
    private Row row(FinancialEntry e,Map<UUID,String> names) {
        return new Row(e.getId(),e.getDescription(),e.getCategory(),e.getContactName(),names.getOrDefault(e.getAccountId(),"Conta"),e.getType(),e.getStatus(),e.getAmount(),e.getDueDate(),e.getPaymentDate(),e.getSequenceNumber(),e.getSeriesCount(),null);
    }
    private Row paymentRow(EntryPayment payment,Map<UUID,String> names) {
        var e=payment.getEntry();
        return new Row(e.getId(),e.getDescription(),e.getCategory(),e.getContactName(),names.getOrDefault(e.getAccountId(),"Conta"),e.getType(),e.getStatus(),payment.getAmount(),e.getDueDate(),payment.getPaymentDate(),e.getSequenceNumber(),e.getSeriesCount(),payment.getId());
    }
    private Totals paymentTotals(Specification<EntryPayment> spec) {
        var cb=em.getCriteriaBuilder();var query=cb.createTupleQuery();var payment=query.from(EntryPayment.class);var entry=payment.join("entry");
        var income=cb.sum(cb.<BigDecimal>selectCase().when(cb.equal(entry.get("type"),INCOME),payment.<BigDecimal>get("amount")).otherwise(BigDecimal.ZERO));
        var expense=cb.sum(cb.<BigDecimal>selectCase().when(cb.equal(entry.get("type"),EXPENSE),payment.<BigDecimal>get("amount")).otherwise(BigDecimal.ZERO));
        query.multiselect(income,expense).where(spec.toPredicate(payment,query,cb));var row=em.createQuery(query).getSingleResult();
        var in=zero(row.get(0));var out=zero(row.get(1));return new Totals(in,out,in.subtract(out),BigDecimal.ZERO,BigDecimal.ZERO);
    }
    private List<CategoryTotal> paymentCategories(Specification<EntryPayment> spec) {
        var cb=em.getCriteriaBuilder();var query=cb.createTupleQuery();var payment=query.from(EntryPayment.class);var entry=payment.join("entry");var total=cb.sum(payment.<BigDecimal>get("amount"));
        query.multiselect(entry.get("category"),total).where(cb.and(spec.toPredicate(payment,query,cb),cb.equal(entry.get("type"),EXPENSE)))
            .groupBy(entry.get("category")).orderBy(cb.desc(total),cb.asc(entry.get("category")));
        return em.createQuery(query).getResultList().stream().map(row->new CategoryTotal((String)row.get(0),zero(row.get(1)))).toList();
    }
    private Sort sort(Filters f){return Sort.by(Sort.Order.asc(f.basis()==Basis.PAYMENT?"paymentDate":"dueDate"),Sort.Order.asc("createdAt"),Sort.Order.asc("id"));}
    private String companyName(){return companies.findById(current.companyId()).orElseThrow().getLegalName();}
    public Report report(Filters f,int page,int size) {
        validate(f);if(page<0 || size<1 || size>100)throw invalid("Paginação inválida.");
        if(f.basis()==Basis.PAYMENT){var spec=paymentFilter(f);var result=payments.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Order.asc("paymentDate"),Sort.Order.asc("createdAt"),Sort.Order.asc("id"))));var names=accountNames();return new Report(companyName(),f,paymentTotals(spec),paymentCategories(spec),result.stream().map(p->paymentRow(p,names)).toList(),result.getTotalElements(),result.getTotalPages(),page);}
        var spec=filter(f);var result=entries.findAll(spec,PageRequest.of(page,size,sort(f)));var names=accountNames();
        return new Report(companyName(),f,totals(spec),categories(spec),result.stream().map(e->row(e,names)).toList(),result.getTotalElements(),result.getTotalPages(),page);
    }
    public Export export(Filters f) {
        validate(f);
        if(f.basis()==Basis.PAYMENT){var spec=paymentFilter(f);var result=payments.findAll(spec,PageRequest.of(0,5001,Sort.by(Sort.Order.asc("paymentDate"),Sort.Order.asc("createdAt"),Sort.Order.asc("id"))));if(result.getTotalElements()>5000)throw invalid("A exportação permite até 5.000 pagamentos. Reduza o período ou refine os filtros.");var names=accountNames();return new Export(companyName(),f,paymentTotals(spec),paymentCategories(spec),result.stream().map(p->paymentRow(p,names)).toList(),f.accountId()==null?"Todas as contas":names.get(f.accountId()));}
        var spec=filter(f);var result=entries.findAll(spec,PageRequest.of(0,5001,sort(f)));
        if(result.getTotalElements()>5000)throw invalid("A exportação permite até 5.000 lançamentos. Reduza o período ou refine os filtros.");
        var names=accountNames();return new Export(companyName(),f,totals(spec),result.stream().map(e->row(e,names)).toList(),f.accountId()==null?"Todas as contas":names.get(f.accountId()));
    }
    public Forecast forecast(int horizon,UUID accountId,boolean includeOverdue) {
        if(horizon<1 || horizon>366)throw invalid("Escolha um horizonte de 1 a 366 dias.");
        var available=finance.accounts();
        if(accountId!=null && available.stream().noneMatch(a->a.id().equals(accountId)))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Conta não encontrada.");
        BigDecimal opening=available.stream().filter(a->accountId==null||a.id().equals(accountId)).map(FinanceService.AccountView::balance).reduce(BigDecimal.ZERO,BigDecimal::add);
        var today=LocalDate.now(ZoneId.of("America/Sao_Paulo"));var end=today.plusDays(horizon);
        BigDecimal overdueIncome=BigDecimal.ZERO,overdueExpense=BigDecimal.ZERO;
        Map<LocalDate,BigDecimal[]> movements=new HashMap<>();
        for(Object[] row:entries.forecast(current.companyId(),accountId,end)) {
            var date=(LocalDate)row[0];var amount=(BigDecimal)row[2];boolean income=row[1]==INCOME;
            if(date.isBefore(today)) {
                if(income)overdueIncome=overdueIncome.add(amount);else overdueExpense=overdueExpense.add(amount);
                if(!includeOverdue)continue;date=today;
            }
            var values=movements.computeIfAbsent(date,key->new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO});int index=income?0:1;values[index]=values[index].add(amount);
        }
        BigDecimal balance=opening,minimum=opening,totalIncome=BigDecimal.ZERO,totalExpense=BigDecimal.ZERO;
        LocalDate negative=opening.signum()<0?today:null;List<Day> days=new ArrayList<>();
        for(var date=today;!date.isAfter(end);date=date.plusDays(1)) {
            var values=movements.getOrDefault(date,new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO});
            totalIncome=totalIncome.add(values[0]);totalExpense=totalExpense.add(values[1]);balance=balance.add(values[0]).subtract(values[1]);
            minimum=minimum.min(balance);if(negative==null && balance.signum()<0)negative=date;
            days.add(new Day(date,values[0],values[1],balance));
        }
        return new Forecast(today,end,opening,balance,totalIncome,totalExpense,overdueIncome,overdueExpense,minimum,negative,includeOverdue,days);
    }
}
