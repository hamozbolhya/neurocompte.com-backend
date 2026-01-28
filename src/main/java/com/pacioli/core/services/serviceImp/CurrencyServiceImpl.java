package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Currency;
import com.pacioli.core.repositories.CurrencyRepository;
import com.pacioli.core.services.CurrencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class CurrencyServiceImpl implements CurrencyService {

    @Autowired
    private CurrencyRepository currencyRepository;

    @Override
    public List<Currency> getAllCurrencies() {
        return currencyRepository.findAll();
    }

    @Override
    public List<Currency> getActiveCurrencies() {
        return currencyRepository.findByActive(true);
    }

    @Override
    public Optional<Currency> getCurrencyByCode(String code) {
        return currencyRepository.findByCode(code);
    }

    @Override
    @Transactional
    public Currency saveCurrency(Currency currency) {
        if (currency.getCreatedDate() == null) {
            currency.setCreatedDate(LocalDate.now());
        }
        return currencyRepository.save(currency);
    }

    @Override
    @Transactional
    public void initializeCurrencies() {
        if (!currencyRepository.hasAnyCurrencies()) {
            log.info("Initializing currency database...");

            List<CurrencyData> currencyDataList = getCommonCurrencies();
            List<Currency> currencies = new ArrayList<>();

            for (CurrencyData data : currencyDataList) {
                Currency currency = new Currency();
                currency.setName(data.getName());
                currency.setCode(data.getCode());
                currency.setCreatedDate(LocalDate.now());
                currency.setActive(true);

                currencies.add(currency);
            }

            currencyRepository.saveAll(currencies);
            log.info("Initialized {} currencies", currencies.size());
        } else {
            log.info("Currencies already exist, skipping initialization");
        }
    }

    /**
     * Data class to hold currency information
     */
    private static class CurrencyData {
        private String name;
        private String code;

        public CurrencyData(String name, String code) {
            this.name = name;
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * Get list of common currencies
     */
    /**
     * Get list of all currencies
     */
    private List<CurrencyData> getCommonCurrencies() {
        return Arrays.asList(
                // Major global currencies
                new CurrencyData("US Dollar", "USD"),
                new CurrencyData("Euro", "EUR"),
                new CurrencyData("Japanese Yen", "JPY"),
                new CurrencyData("British Pound", "GBP"),
                new CurrencyData("Swiss Franc", "CHF"),
                new CurrencyData("Canadian Dollar", "CAD"),
                new CurrencyData("Australian Dollar", "AUD"),
                new CurrencyData("Chinese Yuan", "CNY"),

                // Other currencies alphabetically by code
                new CurrencyData("UAE Dirham", "AED"),
                new CurrencyData("Afghan Afghani", "AFN"),
                new CurrencyData("Albanian Lek", "ALL"),
                new CurrencyData("Armenian Dram", "AMD"),
                new CurrencyData("Netherlands Antillean Guilder", "ANG"),
                new CurrencyData("Angolan Kwanza", "AOA"),
                new CurrencyData("Argentine Peso", "ARS"),
                new CurrencyData("Azerbaijani Manat", "AZN"),
                new CurrencyData("Bosnia-Herzegovina Convertible Mark", "BAM"),
                new CurrencyData("Barbadian Dollar", "BBD"),
                new CurrencyData("Bangladeshi Taka", "BDT"),
                new CurrencyData("Bulgarian Lev", "BGN"),
                new CurrencyData("Bahraini Dinar", "BHD"),
                new CurrencyData("Burundian Franc", "BIF"),
                new CurrencyData("Bermudian Dollar", "BMD"),
                new CurrencyData("Brunei Dollar", "BND"),
                new CurrencyData("Bolivian Boliviano", "BOB"),
                new CurrencyData("Brazilian Real", "BRL"),
                new CurrencyData("Bahamian Dollar", "BSD"),
                new CurrencyData("Bhutanese Ngultrum", "BTN"),
                new CurrencyData("Botswana Pula", "BWP"),
                new CurrencyData("Belarusian Ruble", "BYN"),
                new CurrencyData("Belize Dollar", "BZD"),
                new CurrencyData("Congolese Franc", "CDF"),
                new CurrencyData("Chilean Peso", "CLP"),
                new CurrencyData("Colombian Peso", "COP"),
                new CurrencyData("Costa Rican Colón", "CRC"),
                new CurrencyData("Cuban Peso", "CUP"),
                new CurrencyData("Cape Verdean Escudo", "CVE"),
                new CurrencyData("Czech Koruna", "CZK"),
                new CurrencyData("Djiboutian Franc", "DJF"),
                new CurrencyData("Danish Krone", "DKK"),
                new CurrencyData("Dominican Peso", "DOP"),
                new CurrencyData("Algerian Dinar", "DZD"),
                new CurrencyData("Egyptian Pound", "EGP"),
                new CurrencyData("Eritrean Nakfa", "ERN"),
                new CurrencyData("Ethiopian Birr", "ETB"),
                new CurrencyData("Fijian Dollar", "FJD"),
                new CurrencyData("Falkland Islands Pound", "FKP"),
                new CurrencyData("Georgian Lari", "GEL"),
                new CurrencyData("Ghanaian Cedi", "GHS"),
                new CurrencyData("Gibraltar Pound", "GIP"),
                new CurrencyData("Gambian Dalasi", "GMD"),
                new CurrencyData("Guinean Franc", "GNF"),
                new CurrencyData("Guatemalan Quetzal", "GTQ"),
                new CurrencyData("Guyanese Dollar", "GYD"),
                new CurrencyData("Hong Kong Dollar", "HKD"),
                new CurrencyData("Honduran Lempira", "HNL"),
                new CurrencyData("Croatian Kuna", "HRK"),
                new CurrencyData("Haitian Gourde", "HTG"),
                new CurrencyData("Hungarian Forint", "HUF"),
                new CurrencyData("Indonesian Rupiah", "IDR"),
                new CurrencyData("Israeli New Shekel", "ILS"),
                new CurrencyData("Indian Rupee", "INR"),
                new CurrencyData("Iraqi Dinar", "IQD"),
                new CurrencyData("Iranian Rial", "IRR"),
                new CurrencyData("Icelandic Króna", "ISK"),
                new CurrencyData("Jamaican Dollar", "JMD"),
                new CurrencyData("Jordanian Dinar", "JOD"),
                new CurrencyData("Kenyan Shilling", "KES"),
                new CurrencyData("Kyrgyzstani Som", "KGS"),
                new CurrencyData("Cambodian Riel", "KHR"),
                new CurrencyData("Comorian Franc", "KMF"),
                new CurrencyData("North Korean Won", "KPW"),
                new CurrencyData("South Korean Won", "KRW"),
                new CurrencyData("Kuwaiti Dinar", "KWD"),
                new CurrencyData("Cayman Islands Dollar", "KYD"),
                new CurrencyData("Kazakhstani Tenge", "KZT"),
                new CurrencyData("Lao Kip", "LAK"),
                new CurrencyData("Lebanese Pound", "LBP"),
                new CurrencyData("Sri Lankan Rupee", "LKR"),
                new CurrencyData("Liberian Dollar", "LRD"),
                new CurrencyData("Lesotho Loti", "LSL"),
                new CurrencyData("Libyan Dinar", "LYD"),
                new CurrencyData("Moroccan Dirham", "MAD"),
                new CurrencyData("Moldovan Leu", "MDL"),
                new CurrencyData("Malagasy Ariary", "MGA"),
                new CurrencyData("Macedonian Denar", "MKD"),
                new CurrencyData("Myanmar Kyat", "MMK"),
                new CurrencyData("Mongolian Tugrik", "MNT"),
                new CurrencyData("Macanese Pataca", "MOP"),
                new CurrencyData("Mauritanian Ouguiya", "MRU"),
                new CurrencyData("Mauritian Rupee", "MUR"),
                new CurrencyData("Maldivian Rufiyaa", "MVR"),
                new CurrencyData("Malawian Kwacha", "MWK"),
                new CurrencyData("Mexican Peso", "MXN"),
                new CurrencyData("Malaysian Ringgit", "MYR"),
                new CurrencyData("Mozambican Metical", "MZN"),
                new CurrencyData("Namibian Dollar", "NAD"),
                new CurrencyData("Nigerian Naira", "NGN"),
                new CurrencyData("Nicaraguan Córdoba", "NIO"),
                new CurrencyData("Norwegian Krone", "NOK"),
                new CurrencyData("Nepalese Rupee", "NPR"),
                new CurrencyData("New Zealand Dollar", "NZD"),
                new CurrencyData("Omani Rial", "OMR"),
                new CurrencyData("Panamanian Balboa", "PAB"),
                new CurrencyData("Peruvian Sol", "PEN"),
                new CurrencyData("Papua New Guinean Kina", "PGK"),
                new CurrencyData("Philippine Peso", "PHP"),
                new CurrencyData("Pakistani Rupee", "PKR"),
                new CurrencyData("Polish Złoty", "PLN"),
                new CurrencyData("Paraguayan Guaraní", "PYG"),
                new CurrencyData("Qatari Riyal", "QAR"),
                new CurrencyData("Romanian Leu", "RON"),
                new CurrencyData("Serbian Dinar", "RSD"),
                new CurrencyData("Russian Ruble", "RUB"),
                new CurrencyData("Rwandan Franc", "RWF"),
                new CurrencyData("Saudi Riyal", "SAR"),
                new CurrencyData("Solomon Islands Dollar", "SBD"),
                new CurrencyData("Seychellois Rupee", "SCR"),
                new CurrencyData("Sudanese Pound", "SDG"),
                new CurrencyData("Swedish Krona", "SEK"),
                new CurrencyData("Singapore Dollar", "SGD"),
                new CurrencyData("Saint Helena Pound", "SHP"),
                new CurrencyData("Sierra Leonean Leone", "SLL"),
                new CurrencyData("Somali Shilling", "SOS"),
                new CurrencyData("Surinamese Dollar", "SRD"),
                new CurrencyData("South Sudanese Pound", "SSP"),
                new CurrencyData("São Tomé and Príncipe Dobra", "STN"),
                new CurrencyData("Salvadoran Colón", "SVC"),
                new CurrencyData("Syrian Pound", "SYP"),
                new CurrencyData("Swazi Lilangeni", "SZL"),
                new CurrencyData("Thai Baht", "THB"),
                new CurrencyData("Tajikistani Somoni", "TJS"),
                new CurrencyData("Turkmenistani Manat", "TMT"),
                new CurrencyData("Tunisian Dinar", "TND"),
                new CurrencyData("Tongan Paʻanga", "TOP"),
                new CurrencyData("Turkish Lira", "TRY"),
                new CurrencyData("Trinidad and Tobago Dollar", "TTD"),
                new CurrencyData("New Taiwan Dollar", "TWD"),
                new CurrencyData("Tanzanian Shilling", "TZS"),
                new CurrencyData("Ukrainian Hryvnia", "UAH"),
                new CurrencyData("Ugandan Shilling", "UGX"),
                new CurrencyData("Uruguayan Peso", "UYU"),
                new CurrencyData("Uzbekistani Som", "UZS"),
                new CurrencyData("Venezuelan Bolívar Soberano", "VES"),
                new CurrencyData("Vietnamese Đồng", "VND"),
                new CurrencyData("Vanuatu Vatu", "VUV"),
                new CurrencyData("Samoan Tala", "WST"),
                new CurrencyData("Central African CFA Franc", "XAF"),
                new CurrencyData("East Caribbean Dollar", "XCD"),
                new CurrencyData("West African CFA Franc", "XOF"),
                new CurrencyData("CFP Franc", "XPF"),
                new CurrencyData("Yemeni Rial", "YER"),
                new CurrencyData("South African Rand", "ZAR"),
                new CurrencyData("Zambian Kwacha", "ZMW"),
                new CurrencyData("Zimbabwean Dollar", "ZWL")
        );
    }


}