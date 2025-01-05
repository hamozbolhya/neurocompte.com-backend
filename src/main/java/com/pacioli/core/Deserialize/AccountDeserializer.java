package com.pacioli.core.Deserialize;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.pacioli.core.models.Account;
import com.pacioli.core.repositories.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class AccountDeserializer extends JsonDeserializer<Account> {

    @Autowired
    private AccountRepository accountRepository;

    @Override
    public Account deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String accountName = parser.getText();
        Long dossierId = (Long) context.findInjectableValue("dossierId", null, null); // Get dossierId from the deserialization context

        if (dossierId == null) {
            throw new IllegalArgumentException("Dossier ID is required for account deserialization");
        }

        // Fetch the account using dossierId and account name
        Account account = accountRepository.findByAccountAndDossierId(accountName, dossierId);

        if (account == null) {
            log.warn("No account found for name '{}' and dossier ID {}", accountName, dossierId);
            return null; // Handle this case appropriately (e.g., create a new account if necessary elsewhere)
        }

        return account;
    }

}
