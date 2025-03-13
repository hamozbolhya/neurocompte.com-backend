package com.pacioli.core.services.serviceImp;

import com.pacioli.core.models.Country;
import com.pacioli.core.models.Currency;
import com.pacioli.core.repositories.CountryRepository;
import com.pacioli.core.repositories.CurrencyRepository;
import com.pacioli.core.services.CountryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class CountryServiceImpl implements CountryService {

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private CurrencyRepository currencyRepository;

    // Currency mappings - all countries with their currency codes
    private static final Map<String, String> COUNTRY_CURRENCY_MAP = initCurrencyMap();

    private static Map<String, String> initCurrencyMap() {
        Map<String, String> map = new HashMap<>();

        // Afghanistan to Zimbabwe - all countries with their currency codes
        map.put("AFG", "AFN"); // Afghanistan - Afghan Afghani
        map.put("ZAF", "ZAR"); // South Africa - South African Rand
        map.put("ALB", "ALL"); // Albania - Albanian Lek
        map.put("DZA", "DZD"); // Algeria - Algerian Dinar
        map.put("AND", "EUR"); // Andorra - Euro
        map.put("AGO", "AOA"); // Angola - Angolan Kwanza
        map.put("ATG", "XCD"); // Antigua and Barbuda - East Caribbean Dollar
        map.put("ARG", "ARS"); // Argentina - Argentine Peso
        map.put("ARM", "AMD"); // Armenia - Armenian Dram
        map.put("AUS", "AUD"); // Australia - Australian Dollar
        map.put("AUT", "EUR"); // Austria - Euro
        map.put("AZE", "AZN"); // Azerbaijan - Azerbaijani Manat
        map.put("BHS", "BSD"); // Bahamas - Bahamian Dollar
        map.put("BHR", "BHD"); // Bahrain - Bahraini Dinar
        map.put("BGD", "BDT"); // Bangladesh - Bangladeshi Taka
        map.put("BRB", "BBD"); // Barbados - Barbadian Dollar
        map.put("BLR", "BYN"); // Belarus - Belarusian Ruble
        map.put("BEL", "EUR"); // Belgium - Euro
        map.put("BLZ", "BZD"); // Belize - Belize Dollar
        map.put("BEN", "XOF"); // Benin - West African CFA Franc
        map.put("BTN", "BTN"); // Bhutan - Bhutanese Ngultrum
        map.put("BOL", "BOB"); // Bolivia - Bolivian Boliviano
        map.put("BIH", "BAM"); // Bosnia and Herzegovina - Bosnia and Herzegovina Convertible Mark
        map.put("BWA", "BWP"); // Botswana - Botswana Pula
        map.put("BRA", "BRL"); // Brazil - Brazilian Real
        map.put("BRN", "BND"); // Brunei - Brunei Dollar
        map.put("BGR", "BGN"); // Bulgaria - Bulgarian Lev
        map.put("BFA", "XOF"); // Burkina Faso - West African CFA Franc
        map.put("BDI", "BIF"); // Burundi - Burundian Franc
        map.put("KHM", "KHR"); // Cambodia - Cambodian Riel
        map.put("CMR", "XAF"); // Cameroon - Central African CFA Franc
        map.put("CAN", "CAD"); // Canada - Canadian Dollar
        map.put("CPV", "CVE"); // Cape Verde - Cape Verdean Escudo
        map.put("CAF", "XAF"); // Central African Republic - Central African CFA Franc
        map.put("TCD", "XAF"); // Chad - Central African CFA Franc
        map.put("CHL", "CLP"); // Chile - Chilean Peso
        map.put("CHN", "CNY"); // China - Chinese Yuan
        map.put("COL", "COP"); // Colombia - Colombian Peso
        map.put("COM", "KMF"); // Comoros - Comorian Franc
        map.put("COG", "XAF"); // Congo (Republic) - Central African CFA Franc
        map.put("COD", "CDF"); // Congo (Democratic Republic) - Congolese Franc
        map.put("CRI", "CRC"); // Costa Rica - Costa Rican Colón
        map.put("CIV", "XOF"); // Côte d'Ivoire - West African CFA Franc
        map.put("HRV", "EUR"); // Croatia - Euro (changed from HRK in 2023)
        map.put("CUB", "CUP"); // Cuba - Cuban Peso
        map.put("CYP", "EUR"); // Cyprus - Euro
        map.put("CZE", "CZK"); // Czech Republic - Czech Koruna
        map.put("DNK", "DKK"); // Denmark - Danish Krone
        map.put("DJI", "DJF"); // Djibouti - Djiboutian Franc
        map.put("DMA", "XCD"); // Dominica - East Caribbean Dollar
        map.put("DOM", "DOP"); // Dominican Republic - Dominican Peso
        map.put("ECU", "USD"); // Ecuador - US Dollar
        map.put("EGY", "EGP"); // Egypt - Egyptian Pound
        map.put("SLV", "USD"); // El Salvador - US Dollar
        map.put("GNQ", "XAF"); // Equatorial Guinea - Central African CFA Franc
        map.put("ERI", "ERN"); // Eritrea - Eritrean Nakfa
        map.put("EST", "EUR"); // Estonia - Euro
        map.put("SWZ", "SZL"); // Eswatini - Swazi Lilangeni
        map.put("ETH", "ETB"); // Ethiopia - Ethiopian Birr
        map.put("FJI", "FJD"); // Fiji - Fijian Dollar
        map.put("FIN", "EUR"); // Finland - Euro
        map.put("FRA", "EUR"); // France - Euro
        map.put("GAB", "XAF"); // Gabon - Central African CFA Franc
        map.put("GMB", "GMD"); // Gambia - Gambian Dalasi
        map.put("GEO", "GEL"); // Georgia - Georgian Lari
        map.put("DEU", "EUR"); // Germany - Euro
        map.put("GHA", "GHS"); // Ghana - Ghanaian Cedi
        map.put("GRC", "EUR"); // Greece - Euro
        map.put("GRD", "XCD"); // Grenada - East Caribbean Dollar
        map.put("GTM", "GTQ"); // Guatemala - Guatemalan Quetzal
        map.put("GIN", "GNF"); // Guinea - Guinean Franc
        map.put("GNB", "XOF"); // Guinea-Bissau - West African CFA Franc
        map.put("GUY", "GYD"); // Guyana - Guyanese Dollar
        map.put("HTI", "HTG"); // Haiti - Haitian Gourde
        map.put("HND", "HNL"); // Honduras - Honduran Lempira
        map.put("HUN", "HUF"); // Hungary - Hungarian Forint
        map.put("ISL", "ISK"); // Iceland - Icelandic Króna
        map.put("IND", "INR"); // India - Indian Rupee
        map.put("IDN", "IDR"); // Indonesia - Indonesian Rupiah
        map.put("IRN", "IRR"); // Iran - Iranian Rial
        map.put("IRQ", "IQD"); // Iraq - Iraqi Dinar
        map.put("IRL", "EUR"); // Ireland - Euro
        map.put("ISR", "ILS"); // Israel - Israeli New Shekel
        map.put("ITA", "EUR"); // Italy - Euro
        map.put("JAM", "JMD"); // Jamaica - Jamaican Dollar
        map.put("JPN", "JPY"); // Japan - Japanese Yen
        map.put("JOR", "JOD"); // Jordan - Jordanian Dinar
        map.put("KAZ", "KZT"); // Kazakhstan - Kazakhstani Tenge
        map.put("KEN", "KES"); // Kenya - Kenyan Shilling
        map.put("KIR", "AUD"); // Kiribati - Australian Dollar
        map.put("PRK", "KPW"); // North Korea - North Korean Won
        map.put("KOR", "KRW"); // South Korea - South Korean Won
        map.put("KWT", "KWD"); // Kuwait - Kuwaiti Dinar
        map.put("KGZ", "KGS"); // Kyrgyzstan - Kyrgyzstani Som
        map.put("LAO", "LAK"); // Laos - Lao Kip
        map.put("LVA", "EUR"); // Latvia - Euro
        map.put("LBN", "LBP"); // Lebanon - Lebanese Pound
        map.put("LSO", "LSL"); // Lesotho - Lesotho Loti
        map.put("LBR", "LRD"); // Liberia - Liberian Dollar
        map.put("LBY", "LYD"); // Libya - Libyan Dinar
        map.put("LIE", "CHF"); // Liechtenstein - Swiss Franc
        map.put("LTU", "EUR"); // Lithuania - Euro
        map.put("LUX", "EUR"); // Luxembourg - Euro
        map.put("MKD", "MKD"); // North Macedonia - Macedonian Denar
        map.put("MDG", "MGA"); // Madagascar - Malagasy Ariary
        map.put("MWI", "MWK"); // Malawi - Malawian Kwacha
        map.put("MYS", "MYR"); // Malaysia - Malaysian Ringgit
        map.put("MDV", "MVR"); // Maldives - Maldivian Rufiyaa
        map.put("MLI", "XOF"); // Mali - West African CFA Franc
        map.put("MLT", "EUR"); // Malta - Euro
        map.put("MHL", "USD"); // Marshall Islands - US Dollar
        map.put("MRT", "MRU"); // Mauritania - Mauritanian Ouguiya
        map.put("MUS", "MUR"); // Mauritius - Mauritian Rupee
        map.put("MEX", "MXN"); // Mexico - Mexican Peso
        map.put("FSM", "USD"); // Micronesia - US Dollar
        map.put("MDA", "MDL"); // Moldova - Moldovan Leu
        map.put("MCO", "EUR"); // Monaco - Euro
        map.put("MNG", "MNT"); // Mongolia - Mongolian Tugrik
        map.put("MNE", "EUR"); // Montenegro - Euro
        map.put("MAR", "MAD"); // Morocco - Moroccan Dirham
        map.put("MOZ", "MZN"); // Mozambique - Mozambican Metical
        map.put("MMR", "MMK"); // Myanmar - Myanmar Kyat
        map.put("NAM", "NAD"); // Namibia - Namibian Dollar
        map.put("NRU", "AUD"); // Nauru - Australian Dollar
        map.put("NPL", "NPR"); // Nepal - Nepalese Rupee
        map.put("NLD", "EUR"); // Netherlands - Euro
        map.put("NZL", "NZD"); // New Zealand - New Zealand Dollar
        map.put("NIC", "NIO"); // Nicaragua - Nicaraguan Córdoba
        map.put("NER", "XOF"); // Niger - West African CFA Franc
        map.put("NGA", "NGN"); // Nigeria - Nigerian Naira
        map.put("NOR", "NOK"); // Norway - Norwegian Krone
        map.put("OMN", "OMR"); // Oman - Omani Rial
        map.put("PAK", "PKR"); // Pakistan - Pakistani Rupee
        map.put("PLW", "USD"); // Palau - US Dollar
        map.put("PSE", "ILS"); // Palestine - Israeli New Shekel (also uses Jordanian Dinar)
        map.put("PAN", "PAB"); // Panama - Panamanian Balboa
        map.put("PNG", "PGK"); // Papua New Guinea - Papua New Guinean Kina
        map.put("PRY", "PYG"); // Paraguay - Paraguayan Guaraní
        map.put("PER", "PEN"); // Peru - Peruvian Sol
        map.put("PHL", "PHP"); // Philippines - Philippine Peso
        map.put("POL", "PLN"); // Poland - Polish Złoty
        map.put("PRT", "EUR"); // Portugal - Euro
        map.put("QAT", "QAR"); // Qatar - Qatari Riyal
        map.put("ROU", "RON"); // Romania - Romanian Leu
        map.put("RUS", "RUB"); // Russia - Russian Ruble
        map.put("RWA", "RWF"); // Rwanda - Rwandan Franc
        map.put("KNA", "XCD"); // Saint Kitts and Nevis - East Caribbean Dollar
        map.put("LCA", "XCD"); // Saint Lucia - East Caribbean Dollar
        map.put("VCT", "XCD"); // Saint Vincent and the Grenadines - East Caribbean Dollar
        map.put("WSM", "WST"); // Samoa - Samoan Tala
        map.put("SMR", "EUR"); // San Marino - Euro
        map.put("STP", "STN"); // São Tomé and Príncipe - São Tomé and Príncipe Dobra
        map.put("SAU", "SAR"); // Saudi Arabia - Saudi Riyal
        map.put("SEN", "XOF"); // Senegal - West African CFA Franc
        map.put("SRB", "RSD"); // Serbia - Serbian Dinar
        map.put("SYC", "SCR"); // Seychelles - Seychellois Rupee
        map.put("SLE", "SLL"); // Sierra Leone - Sierra Leonean Leone
        map.put("SGP", "SGD"); // Singapore - Singapore Dollar
        map.put("SVK", "EUR"); // Slovakia - Euro
        map.put("SVN", "EUR"); // Slovenia - Euro
        map.put("SLB", "SBD"); // Solomon Islands - Solomon Islands Dollar
        map.put("SOM", "SOS"); // Somalia - Somali Shilling
        map.put("ESP", "EUR"); // Spain - Euro
        map.put("LKA", "LKR"); // Sri Lanka - Sri Lankan Rupee
        map.put("SDN", "SDG"); // Sudan - Sudanese Pound
        map.put("SSD", "SSP"); // South Sudan - South Sudanese Pound
        map.put("SUR", "SRD"); // Suriname - Surinamese Dollar
        map.put("SWE", "SEK"); // Sweden - Swedish Krona
        map.put("CHE", "CHF"); // Switzerland - Swiss Franc
        map.put("SYR", "SYP"); // Syria - Syrian Pound
        map.put("TWN", "TWD"); // Taiwan - New Taiwan Dollar (not in your list but added for completeness)
        map.put("TJK", "TJS"); // Tajikistan - Tajikistani Somoni
        map.put("TZA", "TZS"); // Tanzania - Tanzanian Shilling
        map.put("THA", "THB"); // Thailand - Thai Baht
        map.put("TLS", "USD"); // Timor-Leste (East Timor) - US Dollar
        map.put("TGO", "XOF"); // Togo - West African CFA Franc
        map.put("TON", "TOP"); // Tonga - Tongan Paʻanga
        map.put("TTO", "TTD"); // Trinidad and Tobago - Trinidad and Tobago Dollar
        map.put("TUN", "TND"); // Tunisia - Tunisian Dinar
        map.put("TUR", "TRY"); // Turkey - Turkish Lira
        map.put("TKM", "TMT"); // Turkmenistan - Turkmenistani Manat
        map.put("TUV", "AUD"); // Tuvalu - Australian Dollar
        map.put("UGA", "UGX"); // Uganda - Ugandan Shilling
        map.put("UKR", "UAH"); // Ukraine - Ukrainian Hryvnia
        map.put("ARE", "AED"); // United Arab Emirates - UAE Dirham
        map.put("GBR", "GBP"); // United Kingdom - British Pound
        map.put("USA", "USD"); // United States - US Dollar
        map.put("URY", "UYU"); // Uruguay - Uruguayan Peso
        map.put("UZB", "UZS"); // Uzbekistan - Uzbekistani Som
        map.put("VUT", "VUV"); // Vanuatu - Vanuatu Vatu
        map.put("VAT", "EUR"); // Vatican City - Euro
        map.put("VEN", "VES"); // Venezuela - Venezuelan Bolívar Soberano
        map.put("VNM", "VND"); // Vietnam - Vietnamese Đồng
        map.put("YEM", "YER"); // Yemen - Yemeni Rial
        map.put("ZMB", "ZMW"); // Zambia - Zambian Kwacha
        map.put("ZWE", "ZWL"); // Zimbabwe - Zimbabwean Dollar

        return map;
    }

    @Override
    public List<Country> getAllCountries() {
        return countryRepository.findAll();
    }

    @Override
    public List<Country> getActiveCountries() {
        return countryRepository.findByActive(true);
    }

    @Override
    public Optional<Country> getCountryByCode(String code) {
        return countryRepository.findByCode(code);
    }

    @Override
    @Transactional
    public Country saveCountry(Country country) {
        if (country.getCreatedDate() == null) {
            country.setCreatedDate(LocalDate.now());
        }
        return countryRepository.save(country);
    }

    @Override
    @Transactional
    public void initializeCountries() {
        if (!countryRepository.hasAnyCountries()) {
            log.info("Initializing country database...");

            // Ensure currencies exist - fetch all currencies
            Map<String, Currency> currencyMap = new HashMap<>();
            for (Currency currency : currencyRepository.findAll()) {
                currencyMap.put(currency.getCode(), currency);
            }

            // If currencies are missing, log a warning
            if (currencyMap.isEmpty()) {
                log.warn("No currencies found in database. Make sure currencies are initialized before countries.");
            }

            List<CountryData> countryDataList = getCountryDataList();
            List<Country> countries = new ArrayList<>();

            for (CountryData data : countryDataList) {
                Country country = new Country();
                country.setName(data.getName());
                country.setCode(data.getCode());
                country.setCreatedDate(LocalDate.now());
                country.setActive(true);

                // Set currency based on mapping
                String currencyCode = COUNTRY_CURRENCY_MAP.get(data.getCode());
                if (currencyCode != null && currencyMap.containsKey(currencyCode)) {
                    country.setCurrency(currencyMap.get(currencyCode));
                    log.debug("Linked country {} with currency {}", data.getCode(), currencyCode);
                } else {
                    log.warn("Could not find currency {} for country {}", currencyCode, data.getCode());
                }

                countries.add(country);
            }

            countryRepository.saveAll(countries);
            log.info("Initialized {} countries", countries.size());
        } else {
            log.info("Countries already exist, skipping initialization");
        }
    }

    @Override
    public List<Country> getCountriesByCurrency(Currency currency) {
        return countryRepository.findByCurrency(currency);
    }

    @Override
    @Transactional
    public Country updateCountryCurrency(String countryCode, String currencyCode) {
        Country country = countryRepository.findByCode(countryCode)
                .orElseThrow(() -> new IllegalArgumentException("Country not found: " + countryCode));

        Currency currency = currencyRepository.findByCode(currencyCode)
                .orElseThrow(() -> new IllegalArgumentException("Currency not found: " + currencyCode));

        country.setCurrency(currency);
        return countryRepository.save(country);
    }

    /**
     * Data class to hold country information
     */
    private static class CountryData {
        private String name;
        private String code;

        public CountryData(String name, String code) {
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

    private List<CountryData> getCountryDataList() {
        return Arrays.asList(
                new CountryData("Afghanistan 🇦🇫", "AFG"),
                new CountryData("Afrique du Sud 🇿🇦", "ZAF"),
                new CountryData("Albanie 🇦🇱", "ALB"),
                new CountryData("Algérie 🇩🇿", "DZA"),
                new CountryData("Allemagne 🇩🇪", "DEU"),
                new CountryData("Andorre 🇦🇩", "AND"),
                new CountryData("Angola 🇦🇴", "AGO"),
                new CountryData("Antigua-et-Barbuda 🇦🇬", "ATG"),
                new CountryData("Arabie Saoudite 🇸🇦", "SAU"),
                new CountryData("Argentine 🇦🇷", "ARG"),
                new CountryData("Arménie 🇦🇲", "ARM"),
                new CountryData("Australie 🇦🇺", "AUS"),
                new CountryData("Autriche 🇦🇹", "AUT"),
                new CountryData("Azerbaïdjan 🇦🇿", "AZE"),
                new CountryData("Bahamas 🇧🇸", "BHS"),
                new CountryData("Bahreïn 🇧🇭", "BHR"),
                new CountryData("Bangladesh 🇧🇩", "BGD"),
                new CountryData("Barbade 🇧🇧", "BRB"),
                new CountryData("Belgique 🇧🇪", "BEL"),
                new CountryData("Belize 🇧🇿", "BLZ"),
                new CountryData("Bénin 🇧🇯", "BEN"),
                new CountryData("Bhoutan 🇧🇹", "BTN"),
                new CountryData("Biélorussie 🇧🇾", "BLR"),
                new CountryData("Bolivie 🇧🇴", "BOL"),
                new CountryData("Bosnie-Herzégovine 🇧🇦", "BIH"),
                new CountryData("Botswana 🇧🇼", "BWA"),
                new CountryData("Brésil 🇧🇷", "BRA"),
                new CountryData("Brunei 🇧🇳", "BRN"),
                new CountryData("Bulgarie 🇧🇬", "BGR"),
                new CountryData("Burkina Faso 🇧🇫", "BFA"),
                new CountryData("Burundi 🇧🇮", "BDI"),
                new CountryData("Cambodge 🇰🇭", "KHM"),
                new CountryData("Cameroun 🇨🇲", "CMR"),
                new CountryData("Canada 🇨🇦", "CAN"),
                new CountryData("Cap-Vert 🇨🇻", "CPV"),
                new CountryData("République centrafricaine 🇨🇫", "CAF"),
                new CountryData("Chili 🇨🇱", "CHL"),
                new CountryData("Chine 🇨🇳", "CHN"),
                new CountryData("Chypre 🇨🇾", "CYP"),
                new CountryData("Colombie 🇨🇴", "COL"),
                new CountryData("Comores 🇰🇲", "COM"),
                new CountryData("Congo (Brazzaville) 🇨🇬", "COG"),
                new CountryData("Congo (Kinshasa) 🇨🇩", "COD"),
                new CountryData("Corée du Nord 🇰🇵", "PRK"),
                new CountryData("Corée du Sud 🇰🇷", "KOR"),
                new CountryData("Costa Rica 🇨🇷", "CRI"),
                new CountryData("Côte d'Ivoire 🇨🇮", "CIV"),
                new CountryData("Croatie 🇭🇷", "HRV"),
                new CountryData("Cuba 🇨🇺", "CUB"),
                new CountryData("Danemark 🇩🇰", "DNK"),
                new CountryData("Djibouti 🇩🇯", "DJI"),
                new CountryData("Dominique 🇩🇲", "DMA"),
                new CountryData("République dominicaine 🇩🇴", "DOM"),
                new CountryData("Égypte 🇪🇬", "EGY"),
                new CountryData("Émirats arabes unis 🇦🇪", "ARE"),
                new CountryData("Équateur 🇪🇨", "ECU"),
                new CountryData("Érythrée 🇪🇷", "ERI"),
                new CountryData("Espagne 🇪🇸", "ESP"),
                new CountryData("Estonie 🇪🇪", "EST"),
                new CountryData("Eswatini 🇸🇿", "SWZ"),
                new CountryData("États-Unis 🇺🇸", "USA"),
                new CountryData("Éthiopie 🇪🇹", "ETH"),
                new CountryData("Fidji 🇫🇯", "FJI"),
                new CountryData("Finlande 🇫🇮", "FIN"),
                new CountryData("France 🇫🇷", "FRA"),
                new CountryData("Gabon 🇬🇦", "GAB"),
                new CountryData("Gambie 🇬🇲", "GMB"),
                new CountryData("Géorgie 🇬🇪", "GEO"),
                new CountryData("Ghana 🇬🇭", "GHA"),
                new CountryData("Grèce 🇬🇷", "GRC"),
                new CountryData("Grenade 🇬🇩", "GRD"),
                new CountryData("Guatemala 🇬🇹", "GTM"),
                new CountryData("Guinée 🇬🇳", "GIN"),
                new CountryData("Guinée-Bissau 🇬🇼", "GNB"),
                new CountryData("Guinée équatoriale 🇬🇶", "GNQ"),
                new CountryData("Guyana 🇬🇾", "GUY"),
                new CountryData("Haïti 🇭🇹", "HTI"),
                new CountryData("Honduras 🇭🇳", "HND"),
                new CountryData("Hongrie 🇭🇺", "HUN"),
                new CountryData("Îles Marshall 🇲🇭", "MHL"),
                new CountryData("Îles Salomon 🇸🇧", "SLB"),
                new CountryData("Inde 🇮🇳", "IND"),
                new CountryData("Indonésie 🇮🇩", "IDN"),
                new CountryData("Iran 🇮🇷", "IRN"),
                new CountryData("Irak 🇮🇶", "IRQ"),
                new CountryData("Irlande 🇮🇪", "IRL"),
                new CountryData("Islande 🇮🇸", "ISL"),
                new CountryData("Italie 🇮🇹", "ITA"),
                new CountryData("Jamaïque 🇯🇲", "JAM"),
                new CountryData("Japon 🇯🇵", "JPN"),
                new CountryData("Jordanie 🇯🇴", "JOR"),
                new CountryData("Kazakhstan 🇰🇿", "KAZ"),
                new CountryData("Kenya 🇰🇪", "KEN"),
                new CountryData("Kirghizistan 🇰🇬", "KGZ"),
                new CountryData("Kiribati 🇰🇮", "KIR"),
                new CountryData("Koweït 🇰🇼", "KWT"),
                new CountryData("Laos 🇱🇦", "LAO"),
                new CountryData("Lesotho 🇱🇸", "LSO"),
                new CountryData("Lettonie 🇱🇻", "LVA"),
                new CountryData("Liban 🇱🇧", "LBN"),
                new CountryData("Libéria 🇱🇷", "LBR"),
                new CountryData("Libye 🇱🇾", "LBY"),
                new CountryData("Liechtenstein 🇱🇮", "LIE"),
                new CountryData("Lituanie 🇱🇹", "LTU"),
                new CountryData("Luxembourg 🇱🇺", "LUX"),
                new CountryData("Macédoine du Nord 🇲🇰", "MKD"),
                new CountryData("Madagascar 🇲🇬", "MDG"),
                new CountryData("Malaisie 🇲🇾", "MYS"),
                new CountryData("Malawi 🇲🇼", "MWI"),
                new CountryData("Maldives 🇲🇻", "MDV"),
                new CountryData("Mali 🇲🇱", "MLI"),
                new CountryData("Malte 🇲🇹", "MLT"),
                new CountryData("Maroc 🇲🇦", "MAR"),
                new CountryData("Maurice 🇲🇺", "MUS"),
                new CountryData("Mauritanie 🇲🇷", "MRT"),
                new CountryData("Mexique 🇲🇽", "MEX"),
                new CountryData("Micronésie 🇫🇲", "FSM"),
                new CountryData("Moldavie 🇲🇩", "MDA"),
                new CountryData("Monaco 🇲🇨", "MCO"),
                new CountryData("Mongolie 🇲🇳", "MNG"),
                new CountryData("Monténégro 🇲🇪", "MNE"),
                new CountryData("Mozambique 🇲🇿", "MOZ"),
                new CountryData("Myanmar (Birmanie) 🇲🇲", "MMR"),
                new CountryData("Namibie 🇳🇦", "NAM"),
                new CountryData("Nauru 🇳🇷", "NRU"),
                new CountryData("Népal 🇳🇵", "NPL"),
                new CountryData("Nicaragua 🇳🇮", "NIC"),
                new CountryData("Niger 🇳🇪", "NER"),
                new CountryData("Nigeria 🇳🇬", "NGA"),
                new CountryData("Norvège 🇳🇴", "NOR"),
                new CountryData("Nouvelle-Zélande 🇳🇿", "NZL"),
                new CountryData("Oman 🇴🇲", "OMN"),
                new CountryData("Ouganda 🇺🇬", "UGA"),
                new CountryData("Ouzbékistan 🇺🇿", "UZB"),
                new CountryData("Pakistan 🇵🇰", "PAK"),
                new CountryData("Palaos 🇵🇼", "PLW"),
                new CountryData("Palestine 🇵🇸", "PSE"),
                new CountryData("Panama 🇵🇦", "PAN"),
                new CountryData("Papouasie-Nouvelle-Guinée 🇵🇬", "PNG"),
                new CountryData("Paraguay 🇵🇾", "PRY"),
                new CountryData("Pays-Bas 🇳🇱", "NLD"),
                new CountryData("Pérou 🇵🇪", "PER"),
                new CountryData("Philippines 🇵🇭", "PHL"),
                new CountryData("Pologne 🇵🇱", "POL"),
                new CountryData("Portugal 🇵🇹", "PRT"),
                new CountryData("Qatar 🇶🇦", "QAT"),
                new CountryData("Roumanie 🇷🇴", "ROU"),
                new CountryData("Royaume-Uni 🇬🇧", "GBR"),
                new CountryData("Russie 🇷🇺", "RUS"),
                new CountryData("Rwanda 🇷🇼", "RWA"),
                new CountryData("Saint-Christophe-et-Niévès 🇰🇳", "KNA"),
                new CountryData("Sainte-Lucie 🇱🇨", "LCA"),
                new CountryData("Saint-Marin 🇸🇲", "SMR"),
                new CountryData("Saint-Vincent-et-les-Grenadines 🇻🇨", "VCT"),
                new CountryData("Salvador 🇸🇻", "SLV"),
                new CountryData("Samoa 🇼🇸", "WSM"),
                new CountryData("São Tomé-et-Principe 🇸🇹", "STP"),
                new CountryData("Sénégal 🇸🇳", "SEN"),
                new CountryData("Serbie 🇷🇸", "SRB"),
                new CountryData("Seychelles 🇸🇨", "SYC"),
                new CountryData("Sierra Leone 🇸🇱", "SLE"),
                new CountryData("Singapour 🇸🇬", "SGP"),
                new CountryData("Slovaquie 🇸🇰", "SVK"),
                new CountryData("Slovénie 🇸🇮", "SVN"),
                new CountryData("Somalie 🇸🇴", "SOM"),
                new CountryData("Soudan 🇸🇩", "SDN"),
                new CountryData("Soudan du Sud 🇸🇸", "SSD"),
                new CountryData("Sri Lanka 🇱🇰", "LKA"),
                new CountryData("Suède 🇸🇪", "SWE"),
                new CountryData("Suisse 🇨🇭", "CHE"),
                new CountryData("Suriname 🇸🇷", "SUR"),
                new CountryData("Syrie 🇸🇾", "SYR"),
                new CountryData("Tadjikistan 🇹🇯", "TJK"),
                new CountryData("Tanzanie 🇹🇿", "TZA"),
                new CountryData("Tchad 🇹🇩", "TCD"),
                new CountryData("République tchèque 🇨🇿", "CZE"),
                new CountryData("Thaïlande 🇹🇭", "THA"),
                new CountryData("Timor oriental 🇹🇱", "TLS"),
                new CountryData("Togo 🇹🇬", "TGO"),
                new CountryData("Tonga 🇹🇴", "TON"),
                new CountryData("Trinité-et-Tobago 🇹🇹", "TTO"),
                new CountryData("Tunisie 🇹🇳", "TUN"),
                new CountryData("Turkménistan 🇹🇲", "TKM"),
                new CountryData("Turquie 🇹🇷", "TUR"),
                new CountryData("Tuvalu 🇹🇻", "TUV"),
                new CountryData("Ukraine 🇺🇦", "UKR"),
                new CountryData("Uruguay 🇺🇾", "URY"),
                new CountryData("Vanuatu 🇻🇺", "VUT"),
                new CountryData("Vatican 🇻🇦", "VAT"),
                new CountryData("Venezuela 🇻🇪", "VEN"),
                new CountryData("Vietnam 🇻🇳", "VNM"),
                new CountryData("Yémen 🇾🇪", "YEM"),
                new CountryData("Zambie 🇿🇲", "ZMB"),
                new CountryData("Zimbabwe 🇿🇼", "ZWE")
        );
    }
}