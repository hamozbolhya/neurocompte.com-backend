package com.pacioli.core.batches.processors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacioli.core.DTO.EcrituresDTO2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EcritureBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EcritureBuilder ecritureBuilder = new EcritureBuilder();

    @Test
    void buildEcrituresKeepsTaxRatePerLine() throws Exception {
        JsonNode ecritures = objectMapper.readTree("""
                [
                  {
                    "Date": "18/02/2026",
                    "JournalCode": "VT",
                    "JournalLib": "Journal des Ventes",
                    "FactureNum": "F2600103",
                    "CompteNum": "34213",
                    "CompteLib": "Client Orion SARL",
                    "EcritLib": "Developpement application web - Phase 1",
                    "DebitAmt": "42000.00",
                    "CreditAmt": "0",
                    "TVARate": "N/A",
                    "Devise": "MAD"
                  },
                  {
                    "Date": "18/02/2026",
                    "JournalCode": "VT",
                    "JournalLib": "Journal des Ventes",
                    "FactureNum": "F2600103",
                    "CompteNum": "7124",
                    "CompteLib": "Ventes de services produits au Maroc",
                    "EcritLib": "Developpement application web - Phase 1",
                    "DebitAmt": "0",
                    "CreditAmt": "35000.00",
                    "TVARate": "20",
                    "Devise": "MAD"
                  },
                  {
                    "Date": "18/02/2026",
                    "JournalCode": "VT",
                    "JournalLib": "Journal des Ventes",
                    "FactureNum": "F2600103",
                    "CompteNum": "4455",
                    "CompteLib": "Etat, TVA facturee",
                    "EcritLib": "TVA 20% sur vente services",
                    "DebitAmt": "0",
                    "CreditAmt": "7000.00",
                    "TVARate": "20",
                    "Devise": "MAD"
                  }
                ]
                """);

        List<EcrituresDTO2> result = ecritureBuilder.buildEcritures(ecritures);

        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getLines().size());
        assertNull(result.get(0).getLines().get(0).getTaxRate());
        assertEquals(20.0, result.get(0).getLines().get(1).getTaxRate());
        assertEquals(20.0, result.get(0).getLines().get(2).getTaxRate());
    }
}
