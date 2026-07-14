@file:Suppress("MaxLineLength")

package com.cloudbasepredictor.web.i18n

class WebForecastStrings(
    weekdayShort: String,
    monthShort: String,
    text: String,
    weather: String,
) {
    val weekdayShort = weekdayShort.split(FIELD_SEPARATOR)
    val monthShort = monthShort.split(FIELD_SEPARATOR)

    private val text = text.split(FIELD_SEPARATOR)
    private val weatherLabels = weather.split(FIELD_SEPARATOR)

    internal operator fun get(index: Int): String = text[index]

    /** The index is [com.cloudbasepredictor.model.WeatherCondition.ordinal]. */
    internal fun weatherLabel(index: Int): String = weatherLabels[index]
}

internal const val FORECAST_TODAY = 0
internal const val FORECAST_DAY_MONTH = 1
internal const val FORECAST_TEMPERATURE_CELSIUS = 2
internal const val FORECAST_PENDING_THERMIC = 3
internal const val FORECAST_PENDING_STUVE = 4
internal const val FORECAST_PENDING_WIND = 5
internal const val FORECAST_PENDING_CLOUD = 6
internal const val FORECAST_SUMMARY_THERMIC = 7
internal const val FORECAST_SUMMARY_STUVE = 8
internal const val FORECAST_SUMMARY_WIND = 9
internal const val FORECAST_SUMMARY_CLOUD = 10
private const val FIELD_SEPARATOR = '|'

val englishWebForecastStrings = WebForecastStrings(
    weekdayShort = "Sun|Mon|Tue|Wed|Thu|Fri|Sat",
    monthShort = "Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec",
    text = "Today|{day} {month}|{value}°C|Forecast content for {place} will appear here.|Stuve forecast content for {place} will appear here.|Wind forecast content for {place} will appear here.|Cloud forecast content for {place} will appear here.|{day} in {place}. {weather}. High {high}, low {low}. Thermic profile is ready for the selected altitude range.|{day} in {place}. {weather}. Stuve diagram is ready for the selected hour.|{day} in {place}. {weather}. Wind profile is ready for the selected altitude range.|{day} in {place}. {weather}. Cloud layers, radiation, sunshine, and precipitation are ready.",
    weather = "Clear sky|Partly cloudy|Fog|Drizzle|Rain|Snow|Rain showers|Snow showers|Thunderstorm|Unknown weather",
)

val germanWebForecastStrings = WebForecastStrings(
    weekdayShort = "So|Mo|Di|Mi|Do|Fr|Sa",
    monthShort = "Jan|Feb|Mär|Apr|Mai|Jun|Jul|Aug|Sep|Okt|Nov|Dez",
    text = "Heute|{day} {month}|{value}°C|Vorhersageinhalt für {place} erscheint hier.|Stuve-Vorhersageinhalt für {place} erscheint hier.|Wind-Vorhersageinhalt für {place} erscheint hier.|Wolken-Vorhersageinhalt für {place} erscheint hier.|{day} in {place}. {weather}. Höchstwert {high}, Tiefstwert {low}. Das Thermikprofil ist für den gewählten Höhenbereich bereit.|{day} in {place}. {weather}. Das Stuve-Diagramm ist für die gewählte Stunde bereit.|{day} in {place}. {weather}. Das Windprofil ist für den gewählten Höhenbereich bereit.|{day} in {place}. {weather}. Wolkenschichten, Strahlung, Sonnenschein und Niederschlag sind bereit.",
    weather = "Klarer Himmel|Teilweise bewölkt|Nebel|Nieselregen|Regen|Schnee|Regenschauer|Schneeschauer|Gewitter|Unbekanntes Wetter",
)

val spanishWebForecastStrings = WebForecastStrings(
    weekdayShort = "dom|lun|mar|mié|jue|vie|sáb",
    monthShort = "ene|feb|mar|abr|may|jun|jul|ago|sept|oct|nov|dic",
    text = "Hoy|{day} {month}|{value}°C|El contenido del pronóstico para {place} aparecerá aquí.|El contenido del pronóstico Stuve para {place} aparecerá aquí.|El contenido del pronóstico de viento para {place} aparecerá aquí.|El contenido del pronóstico de nubes para {place} aparecerá aquí.|{day} en {place}. {weather}. Máxima {high}, mínima {low}. El perfil térmico está listo para el rango de altitud seleccionado.|{day} en {place}. {weather}. El diagrama Stuve está listo para la hora seleccionada.|{day} en {place}. {weather}. El perfil de viento está listo para el rango de altitud seleccionado.|{day} en {place}. {weather}. Las capas de nubes, la radiación, el sol y la precipitación están listas.",
    weather = "Cielo despejado|Parcialmente nublado|Niebla|Llovizna|Lluvia|Nieve|Chubascos|Chubascos de nieve|Tormenta|Tiempo desconocido",
)

val frenchWebForecastStrings = WebForecastStrings(
    weekdayShort = "dim.|lun.|mar.|mer.|jeu.|ven.|sam.",
    monthShort = "janv.|févr.|mars|avr.|mai|juin|juil.|août|sept.|oct.|nov.|déc.",
    text = "Aujourd’hui|{day} {month}|{value}°C|Le contenu des prévisions pour {place} apparaîtra ici.|Le contenu des prévisions Stuve pour {place} apparaîtra ici.|Le contenu des prévisions de vent pour {place} apparaîtra ici.|Le contenu des prévisions de nuages pour {place} apparaîtra ici.|{day} à {place}. {weather}. Maximale {high}, minimale {low}. Le profil thermique est prêt pour la plage d’altitude sélectionnée.|{day} à {place}. {weather}. Le diagramme Stuve est prêt pour l’heure sélectionnée.|{day} à {place}. {weather}. Le profil de vent est prêt pour la plage d’altitude sélectionnée.|{day} à {place}. {weather}. Les couches nuageuses, le rayonnement, l’ensoleillement et les précipitations sont prêts.",
    weather = "Ciel dégagé|Partiellement nuageux|Brouillard|Bruine|Pluie|Neige|Averses|Averses de neige|Orage|Temps inconnu",
)

val portugueseWebForecastStrings = WebForecastStrings(
    weekdayShort = "dom.|seg.|ter.|qua.|qui.|sex.|sáb.",
    monthShort = "jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez",
    text = "Hoje|{day} {month}|{value}°C|O conteúdo da previsão para {place} aparecerá aqui.|O conteúdo da previsão Stuve para {place} aparecerá aqui.|O conteúdo da previsão de vento para {place} aparecerá aqui.|O conteúdo da previsão de nuvens para {place} aparecerá aqui.|{day} em {place}. {weather}. Máxima {high}, mínima {low}. O perfil térmico está pronto para o intervalo de altitude selecionado.|{day} em {place}. {weather}. O diagrama Stuve está pronto para a hora selecionada.|{day} em {place}. {weather}. O perfil de vento está pronto para o intervalo de altitude selecionado.|{day} em {place}. {weather}. As camadas de nuvens, a radiação, o sol e a precipitação estão prontos.",
    weather = "Céu limpo|Parcialmente nublado|Nevoeiro|Chuvisco|Chuva|Neve|Aguaceiros|Aguaceiros de neve|Trovoada|Tempo desconhecido",
)

val russianWebForecastStrings = WebForecastStrings(
    weekdayShort = "вс|пн|вт|ср|чт|пт|сб",
    monthShort = "янв.|февр.|мар.|апр.|мая|июн.|июл.|авг.|сент.|окт.|нояб.|дек.",
    text = "Сегодня|{day} {month}|{value}°C|Содержимое прогноза для {place} появится здесь.|Содержимое прогноза Stuve для {place} появится здесь.|Содержимое прогноза ветра для {place} появится здесь.|Содержимое прогноза облачности для {place} появится здесь.|{day}, {place}. {weather}. Максимум {high}, минимум {low}. Профиль термичности готов для выбранного диапазона высот.|{day}, {place}. {weather}. Диаграмма Stuve готова для выбранного часа.|{day}, {place}. {weather}. Профиль ветра готов для выбранного диапазона высот.|{day}, {place}. {weather}. Слои облаков, радиация, солнце и осадки готовы.",
    weather = "Ясное небо|Переменная облачность|Туман|Морось|Дождь|Снег|Ливневый дождь|Снежные ливни|Гроза|Неизвестная погода",
)

val georgianWebForecastStrings = WebForecastStrings(
    weekdayShort = "კვი|ორშ|სამ|ოთხ|ხუთ|პარ|შაბ",
    monthShort = "იან|თებ|მარ|აპრ|მაი|ივნ|ივლ|აგვ|სექ|ოქტ|ნოე|დეკ",
    text = "დღეს|{day} {month}|{value}°C|{place}-ის პროგნოზის შიგთავსი აქ გამოჩნდება.|{place}-ის Stuve-ის პროგნოზის შიგთავსი აქ გამოჩნდება.|{place}-ის ქარის პროგნოზის შიგთავსი აქ გამოჩნდება.|{place}-ის ღრუბლების პროგნოზის შიგთავსი აქ გამოჩნდება.|{day}, {place}. {weather}. მაქსიმუმი {high}, მინიმუმი {low}. თერმიკის პროფილი მზადაა არჩეული სიმაღლის დიაპაზონისთვის.|{day}, {place}. {weather}. Stuve-ის დიაგრამა მზადაა არჩეული საათისთვის.|{day}, {place}. {weather}. ქარის პროფილი მზადაა არჩეული სიმაღლის დიაპაზონისთვის.|{day}, {place}. {weather}. ღრუბლების ფენები, რადიაცია, მზის შუქი და ნალექი მზადაა.",
    weather = "მოწმენდილი ცა|ნაწილობრივ ღრუბლიანი|ნისლი|ჟინჟღლი|წვიმა|თოვლი|ხანმოკლე წვიმა|ხანმოკლე თოვლი|ჭექა-ქუხილი|უცნობი ამინდი",
)

val thaiWebForecastStrings = WebForecastStrings(
    weekdayShort = "อา.|จ.|อ.|พ.|พฤ.|ศ.|ส.",
    monthShort = "ม.ค.|ก.พ.|มี.ค.|เม.ย.|พ.ค.|มิ.ย.|ก.ค.|ส.ค.|ก.ย.|ต.ค.|พ.ย.|ธ.ค.",
    text = "วันนี้|{day} {month}|{value}°C|เนื้อหาการพยากรณ์สำหรับ {place} จะแสดงที่นี่|เนื้อหาการพยากรณ์ Stuve สำหรับ {place} จะแสดงที่นี่|เนื้อหาการพยากรณ์ลมสำหรับ {place} จะแสดงที่นี่|เนื้อหาการพยากรณ์เมฆสำหรับ {place} จะแสดงที่นี่|{day} ที่ {place} {weather} สูงสุด {high} ต่ำสุด {low} โปรไฟล์ความร้อนพร้อมสำหรับช่วงระดับความสูงที่เลือก|{day} ที่ {place} {weather} แผนภาพ Stuve พร้อมสำหรับชั่วโมงที่เลือก|{day} ที่ {place} {weather} โปรไฟล์ลมพร้อมสำหรับช่วงระดับความสูงที่เลือก|{day} ที่ {place} {weather} ชั้นเมฆ การแผ่รังสี แสงแดด และหยาดน้ำฟ้าพร้อมแล้ว",
    weather = "ท้องฟ้าแจ่มใส|มีเมฆบางส่วน|หมอก|ฝนละออง|ฝน|หิมะ|ฝนตกเป็นแห่ง ๆ|หิมะตกเป็นแห่ง ๆ|พายุฝนฟ้าคะนอง|ไม่ทราบสภาพอากาศ",
)

val chineseWebForecastStrings = WebForecastStrings(
    weekdayShort = "周日|周一|周二|周三|周四|周五|周六",
    monthShort = "1月|2月|3月|4月|5月|6月|7月|8月|9月|10月|11月|12月",
    text = "今天|{month}{day}日|{value}°C|{place} 的预报内容将显示在此处。|{place} 的 Stuve 预报内容将显示在此处。|{place} 的风预报内容将显示在此处。|{place} 的云预报内容将显示在此处。|{day}在{place}。{weather}。最高 {high}，最低 {low}。热气流剖面已针对所选高度范围就绪。|{day}在{place}。{weather}。Stuve 图已针对所选时刻就绪。|{day}在{place}。{weather}。风廓线已针对所选高度范围就绪。|{day}在{place}。{weather}。云层、辐射、日照和降水已就绪。",
    weather = "晴|多云|雾|毛毛雨|雨|雪|阵雨|阵雪|雷暴|未知天气",
)
