package com.fintry.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CsvEntryParserTests {
  final String header = "descricao;tipo;valor;vencimento;categoria\r\n";
  final String line = "Serviço;receita;1.234,56;20/10/2026;Serviços\r\n";

  @Test
  void readsBomAccentsCrLfQuotesAndMultilineFields() {
    var rows =
        CsvEntryParser.parse(
            "\ufeffDescrição;Tipo;Valor;Vencimento;Categoria\r\n\r\n"
                + "\"Serviço; de \"\"design\"\"\n"
                + "mensal\";receita;1.234,56;20/10/2026;Serviços\r\n");
    assertEquals(1, rows.size());
    assertEquals(2, rows.get(0).number());
    assertEquals("Serviço; de \"design\"\nmensal", rows.get(0).fields().get("descricao"));
    assertEquals(
        "1234.56", CsvEntryParser.amount(rows.get(0).fields().get("valor")).toPlainString());
    var comma =
        CsvEntryParser.parse(
            "descricao,tipo,valor,vencimento,categoria\nTeste,despesa,\"12,50\",2026-10-20,Outros");
    assertEquals("12,50", comma.get(0).fields().get("valor"));
  }

  @Test
  void detectsDelimiterAfterBlankLinesAndWithCrOnlyLineEndings() {
    var rows =
        CsvEntryParser.parse(
            "\r\n"
                + "  \r\n"
                + "descricao;tipo;valor;vencimento;categoria\r\n"
                + "Teste;receita;10;2026-10-20;Outros");
    assertEquals(1, rows.size());
    var comma =
        CsvEntryParser.parse(
            "descricao,tipo,valor,vencimento,categoria\r"
                + "Texto; com ponto e vírgula,receita,10,2026-10-20,Outros");
    assertEquals("Texto; com ponto e vírgula", comma.get(0).fields().get("descricao"));
  }

  @Test
  void rejectsBrokenStructureAndUnsupportedHeaders() {
    for (var csv :
        List.of(
            "",
            header,
            header + "a;b;c",
            header + "\"a;b;c",
            header + "a\"b;receita;1;2026-10-20;X",
            header + "\"a\"b;receita;1;2026-10-20;X",
            "descricao;tipo;valor;vencimento;categoria;categoria\na;b;c;d;e;f",
            "descricao;tipo;valor;vencimento;contato\na;b;c;d;e",
            header + "a;receita;1;2026-10-20;\ufffd")) {
      assertThrows(ResponseStatusException.class, () -> CsvEntryParser.parse(csv), csv);
    }
  }

  @Test
  void boundsBytesAndRecordsIncludingUtf8AndBlankLines() {
    assertEquals(500, CsvEntryParser.parse(header + line.repeat(500)).size());
    assertThrows(
        ResponseStatusException.class, () -> CsvEntryParser.parse(header + line.repeat(501)));
    assertThrows(
        ResponseStatusException.class, () -> CsvEntryParser.parse(header + "é".repeat(600_000)));
    assertThrows(
        ResponseStatusException.class, () -> CsvEntryParser.parse(header + "x".repeat(1_048_577)));
  }

  @Test
  void validatesExactMoneyAndStrictCalendarDates() {
    assertEquals("1234.56", CsvEntryParser.amount("1.234,56").toPlainString());
    assertEquals("1.00", CsvEntryParser.amount("1").toPlainString());
    for (var value :
        List.of("-1", "0", "1.234", "1,234", "1.23,45", "R$ 10,00", "1e3", "1000000000000", "NaN"))
      assertThrows(IllegalArgumentException.class, () -> CsvEntryParser.amount(value), value);
    assertEquals("2024-02-29", CsvEntryParser.date("29/02/2024").toString());
    assertEquals("2026-10-20", CsvEntryParser.date("2026-10-20").toString());
    for (var value :
        List.of("29/02/2026", "31/04/2026", "2026-2-01", "20/10/26", "2101-01-01", "1899-12-31"))
      assertThrows(IllegalArgumentException.class, () -> CsvEntryParser.date(value), value);
  }
}
