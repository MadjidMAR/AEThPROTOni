package io.github.immaghzbad.aetherst.shared.util

object CountryNames {
    private val map = mapOf(
        "AD" to "Andorra", "AE" to "United Arab Emirates", "AF" to "Afghanistan", "AG" to "Antigua and Barbuda",
        "AI" to "Anguilla", "AL" to "Albania", "AM" to "Armenia", "AO" to "Angola", "AR" to "Argentina",
        "AT" to "Austria", "AU" to "Australia", "AZ" to "Azerbaijan", "BA" to "Bosnia and Herzegovina",
        "BB" to "Barbados", "BD" to "Bangladesh", "BE" to "Belgium", "BF" to "Burkina Faso", "BG" to "Bulgaria",
        "BH" to "Bahrain", "BI" to "Burundi", "BJ" to "Benin", "BM" to "Bermuda", "BN" to "Brunei",
        "BO" to "Bolivia", "BR" to "Brazil", "BS" to "Bahamas", "BT" to "Bhutan", "BW" to "Botswana",
        "BY" to "Belarus", "BZ" to "Belize", "CA" to "Canada", "CD" to "DR Congo", "CG" to "Congo",
        "CH" to "Switzerland", "CI" to "Côte d'Ivoire", "CL" to "Chile", "CM" to "Cameroon", "CN" to "China",
        "CO" to "Colombia", "CR" to "Costa Rica", "CY" to "Cyprus", "CZ" to "Czechia", "DE" to "Germany",
        "DJ" to "Djibouti", "DK" to "Denmark", "DM" to "Dominica", "DO" to "Dominican Republic", "DZ" to "Algeria",
        "EC" to "Ecuador", "EE" to "Estonia", "EG" to "Egypt", "ER" to "Eritrea", "ES" to "Spain",
        "ET" to "Ethiopia", "FI" to "Finland", "FJ" to "Fiji", "FO" to "Faroe Islands", "FR" to "France",
        "GA" to "Gabon", "GB" to "United Kingdom", "GD" to "Grenada", "GE" to "Georgia", "GG" to "Guernsey",
        "GH" to "Ghana", "GI" to "Gibraltar", "GL" to "Greenland", "GM" to "Gambia", "GN" to "Guinea",
        "GP" to "Guadeloupe", "GR" to "Greece", "GT" to "Guatemala", "GU" to "Guam", "GW" to "Guinea-Bissau",
        "GY" to "Guyana", "HK" to "Hong Kong", "HN" to "Honduras", "HR" to "Croatia", "HT" to "Haiti",
        "HU" to "Hungary", "ID" to "Indonesia", "IE" to "Ireland", "IL" to "Israel", "IM" to "Isle of Man",
        "IN" to "India", "IQ" to "Iraq", "IS" to "Iceland", "IT" to "Italy", "JE" to "Jersey",
        "JM" to "Jamaica", "JO" to "Jordan", "JP" to "Japan", "KE" to "Kenya", "KG" to "Kyrgyzstan",
        "KH" to "Cambodia", "KI" to "Kiribati", "KM" to "Comoros", "KN" to "Saint Kitts and Nevis", "KR" to "South Korea",
        "KW" to "Kuwait", "KY" to "Cayman Islands", "KZ" to "Kazakhstan", "LA" to "Laos", "LB" to "Lebanon",
        "LC" to "Saint Lucia", "LI" to "Liechtenstein", "LK" to "Sri Lanka", "LR" to "Liberia", "LS" to "Lesotho",
        "LT" to "Lithuania", "LU" to "Luxembourg", "LV" to "Latvia", "LY" to "Libya", "MA" to "Morocco",
        "MC" to "Monaco", "MD" to "Moldova", "ME" to "Montenegro", "MG" to "Madagascar", "MK" to "North Macedonia",
        "ML" to "Mali", "MM" to "Myanmar", "MN" to "Mongolia", "MQ" to "Martinique", "MT" to "Malta",
        "MU" to "Mauritius", "MV" to "Maldives", "MW" to "Malawi", "MX" to "Mexico", "MY" to "Malaysia",
        "MZ" to "Mozambique", "NA" to "Namibia", "NC" to "New Caledonia", "NE" to "Niger", "NG" to "Nigeria",
        "NI" to "Nicaragua", "NL" to "Netherlands", "NO" to "Norway", "NP" to "Nepal", "NZ" to "New Zealand",
        "OM" to "Oman", "PA" to "Panama", "PE" to "Peru", "PF" to "French Polynesia", "PG" to "Papua New Guinea",
        "PH" to "Philippines", "PK" to "Pakistan", "PL" to "Poland", "PR" to "Puerto Rico", "PT" to "Portugal",
        "PY" to "Paraguay", "QA" to "Qatar", "RE" to "Réunion", "RO" to "Romania", "RS" to "Serbia",
        "RU" to "Russia", "RW" to "Rwanda", "SA" to "Saudi Arabia", "SB" to "Solomon Islands", "SC" to "Seychelles",
        "SE" to "Sweden", "SG" to "Singapore", "SI" to "Slovenia", "SK" to "Slovakia", "SL" to "Sierra Leone",
        "SM" to "San Marino", "SN" to "Senegal", "SO" to "Somalia", "SR" to "Suriname", "SS" to "South Sudan",
        "ST" to "Sao Tome and Principe", "SV" to "El Salvador", "SX" to "Sint Maarten", "SY" to "Syria", "SZ" to "Eswatini",
        "TC" to "Turks and Caicos Islands", "TG" to "Togo", "TH" to "Thailand", "TJ" to "Tajikistan", "TL" to "Timor-Leste",
        "TM" to "Turkmenistan", "TN" to "Tunisia", "TO" to "Tonga", "TR" to "Turkey", "TT" to "Trinidad and Tobago",
        "TW" to "Taiwan", "TZ" to "Tanzania", "UA" to "Ukraine", "UG" to "Uganda", "US" to "United States",
        "UY" to "Uruguay", "UZ" to "Uzbekistan", "VA" to "Vatican City", "VC" to "Saint Vincent and the Grenadines",
        "VE" to "Venezuela", "VG" to "British Virgin Islands", "VI" to "U.S. Virgin Islands", "VN" to "Vietnam",
        "VU" to "Vanuatu", "WF" to "Wallis and Futuna", "WS" to "Samoa", "XK" to "Kosovo", "YE" to "Yemen",
        "ZA" to "South Africa", "ZM" to "Zambia", "ZW" to "Zimbabwe"
    )

    fun display(code: String): String {
        val c = code.trim().uppercase()
        return map[c] ?: c
    }

    fun label(code: String): String {
        val c = code.trim().uppercase()
        return if (c.isEmpty()) "Auto" else "${display(c)} ($c)"
    }
}
