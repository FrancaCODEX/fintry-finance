package com.fintry.finance;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class SeriesScheduleTests {
    @Test void preservesOriginalDayAcrossShortAndLeapMonths() {
        var leap=SeriesService.schedule(FinancialSeries.Kind.RECURRING,3,new BigDecimal("10"),LocalDate.of(2028,1,31));
        assertEquals(LocalDate.of(2028,2,29),leap.get(1).dueDate());assertEquals(LocalDate.of(2028,3,31),leap.get(2).dueDate());
        var normal=SeriesService.schedule(FinancialSeries.Kind.RECURRING,3,new BigDecimal("10"),LocalDate.of(2027,1,30));
        assertEquals(LocalDate.of(2027,2,28),normal.get(1).dueDate());assertEquals(LocalDate.of(2027,3,30),normal.get(2).dueDate());
    }
    @Test void distributesRemainderWithoutInventingOrLosingMoney() {
        var plan=SeriesService.schedule(FinancialSeries.Kind.INSTALLMENT,120,new BigDecimal("999999999999.99"),LocalDate.of(2026,10,31));
        assertEquals(0,new BigDecimal("999999999999.99").compareTo(plan.stream().map(SeriesService.Item::amount).reduce(BigDecimal.ZERO,BigDecimal::add)));
        assertTrue(plan.stream().allMatch(item->item.amount().scale()==2 && item.amount().signum()>0));
        assertThrows(IllegalArgumentException.class,()->SeriesService.schedule(FinancialSeries.Kind.INSTALLMENT,120,new BigDecimal("1"),LocalDate.now()));
    }
}
