package com.fintry.finance;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OfxParserTests {
  String row =
      "<STMTTRN><DTPOSTED>20261019120000[-3:BRT]<TRNAMT>-1234.56<FITID>123<NAME>Caf&#233; &amp;"
          + " Cia</STMTTRN>";

  String file(String rows) {
    return "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL<BANKACCTFROM><BANKID>001<ACCTID>12345<ACCTTYPE>CHECKING</BANKACCTFROM><BANKTRANLIST>"
        + rows
        + "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";
  }

  @Test
  void readsSgmlAndXmlPreservingSignIdentifiersAndEntities() {
    var parsed = OfxParser.parse(file(row));
    assertEquals("Café & Cia", parsed.rows().get(0).description());
    assertEquals("-1234.56", parsed.rows().get(0).amount().toPlainString());
    assertEquals("••••2345", parsed.accountHint());
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + file(row)
                .replaceAll(
                    "<(CURDEF|BANKID|ACCTID|ACCTTYPE|DTPOSTED|TRNAMT|FITID|NAME)>([^<]+)",
                    "<$1>$2</$1>");
    assertEquals(parsed, OfxParser.parse(xml));
  }

  @Test
  void rejectsMalformedMultiAccountCurrenciesEntitiesAndOversizedFiles() {
    for (var text :
        new String[] {
          file(row).replace("BRL", "USD"),
          file(row) + file(row),
          file(row).replace("</OFX>", ""),
          file(row).replace("<BANKID>001", "<BANKID>001<BANKID>002"),
          file(row).replace("<BANKTRANLIST>", "<BANKTRANLIST><STMTRS></STMTRS>"),
          "<!DOCTYPE OFX [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" + file(row),
          file(row.repeat(501)),
          file(row).replace("<OFX>", "<OFX evil='1'>"),
          "a".repeat(1048577)
        })
      assertThrows(
          ResponseStatusException.class,
          () -> OfxParser.parse(text),
          text.substring(0, Math.min(80, text.length())));
  }

  @Test
  void marksBadAmountsDatesMissingIdsAndCorrectionsWithoutDiscardingValidRows() {
    for (var invalid :
        new String[] {
          row.replace("-1234.56", "1.234,56"),
          row.replace("-1234.56", "0"),
          row.replace("-1234.56", "0.001"),
          row.replace("20261019", "20260230"),
          row.replace("20261019", "20991231"),
          row.replace("<FITID>123", "<FITID>"),
          row.replace("<NAME>", "<CORRECTFITID>old<NAME>"),
          row.replace("&#233;", "&unknown;")
        }) {
      var p = OfxParser.parse(file(invalid + row));
      assertNotNull(p.rows().get(0).error());
      assertNull(p.rows().get(1).error());
    }
  }
}
