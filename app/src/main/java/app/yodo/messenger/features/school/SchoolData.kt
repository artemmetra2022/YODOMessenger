package app.yodo.messenger.features.school

/**
 * НОВОЕ (раздел «Школа»): статические данные школьного справочника гимназии №196,
 * перенесённые из Telegram-бота (Bot3.4.py). Всё, что редко меняется (список
 * учителей, расписание звонков, вопросы викторины, FAQ, контакты), вшито в
 * приложение и обновляется вместе с новой версией. Динамический контент
 * (новости, опросы, идеи, оценки) живёт в Firestore — см. SchoolRepository.
 */

data class SchoolTeacherEntry(
    val name: String,
    val url: String? = null,
    val extra: String? = null
)

data class SchoolSubjectEntry(
    val name: String,
    val teachers: List<SchoolTeacherEntry>
)

object SchoolData {

    const val SCHOOL_NAME = "Гимназия №196 Красногвардейского района Санкт-Петербурга"
    const val SCHOOL_ADDRESS = "Санкт-Петербург, пр. Ударников, 31"
    const val SCHOOL_PHONE = "+7 (812) 417-22-02"
    const val SCHOOL_PHONE_DIAL = "tel:+78124172202"
    const val SCHOOL_EMAIL = "school196@bk.ru"
    const val SCHOOL_SITE = "https://196spb.edusite.ru/"
    const val SCHOOL_VK = "https://vk.com/gym196"
    const val LESSONS_SCHEDULE_URL = "https://drive.google.com/file/d/1YjKO0N7Pbvq2IHAkSy5cpr2cwvisV0OT/view"

    // Дата ближайших каникул (меняется по четвертям, как в боте HOLIDAY_DATE).
    const val HOLIDAY_DATE_ISO = "2026-10-26"

    val SUBJECTS: List<SchoolSubjectEntry> = listOf(
        SchoolSubjectEntry("Математика", listOf(
            SchoolTeacherEntry("Деянова И.С.", "https://sites.google.com/site/ucitelskijklub196/kafedra-matematiki/deanova-i-s"),
            SchoolTeacherEntry("Иванова О.И.", "https://sites.google.com/site/ucitelskijklub196/kafedra-matematiki/ivanova-o-i"),
            SchoolTeacherEntry("Корчагина С.М.", "https://sites.google.com/site/ucitelskijklub196/kafedra-matematiki/korcagina-svetlana-mihajlovna"),
            SchoolTeacherEntry("Ефимов С.В.")
        )),
        SchoolSubjectEntry("Русский Язык и Литература", listOf(
            SchoolTeacherEntry("Егорова С.В.", "https://sites.google.com/site/ucitelskijklub196/%D1%80%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9-%D1%8F%D0%B7%D1%8B%D0%BA-%D0%B8-%D0%BB%D0%B8%D1%82%D0%B5%D1%80%D0%B0%D1%82%D1%83%D1%80%D0%B0/egorova-s-v"),
            SchoolTeacherEntry("Леонтьева И.Г.", "https://sites.google.com/site/ucitelskijklub196/%D1%80%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9-%D1%8F%D0%B7%D1%8B%D0%BA-%D0%B8-%D0%BB%D0%B8%D1%82%D0%B5%D1%80%D0%B0%D1%82%D1%83%D1%80%D0%B0/leonteva-i-g"),
            SchoolTeacherEntry("Паневина Л.В.", "https://sites.google.com/site/ucitelskijklub196/%D1%80%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9-%D1%8F%D0%B7%D1%8B%D0%BA-%D0%B8-%D0%BB%D0%B8%D1%82%D0%B5%D1%80%D0%B0%D1%82%D1%83%D1%80%D0%B0/panevina-l-v"),
            SchoolTeacherEntry("Пищенко Л.В.", "https://sites.google.com/site/ucitelskijklub196/%D1%80%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9-%D1%8F%D0%B7%D1%8B%D0%BA-%D0%B8-%D0%BB%D0%B8%D1%82%D0%B5%D1%80%D0%B0%D1%82%D1%83%D1%80%D0%B0/pisenko-l-v"),
            SchoolTeacherEntry("Селицкая В.В.", "https://sites.google.com/site/ucitelskijklub196/%D1%80%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9-%D1%8F%D0%B7%D1%8B%D0%BA-%D0%B8-%D0%BB%D0%B8%D1%82%D0%B5%D1%80%D0%B0%D1%82%D1%83%D1%80%D0%B0/selickaa-v-v", extra = "Также: https://nsportal.ru/selitskaya-viktoriya-valerevna"),
            SchoolTeacherEntry("Шевченко С.Ф.", "https://sites.google.com/site/ucitelskijklub196/%D1%80%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9-%D1%8F%D0%B7%D1%8B%D0%BA-%D0%B8-%D0%BB%D0%B8%D1%82%D0%B5%D1%80%D0%B0%D1%82%D1%83%D1%80%D0%B0/sevcenko-s-f")
        )),
        SchoolSubjectEntry("Иностранные Языки", listOf(
            SchoolTeacherEntry("Бондаренко Л.И.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/a-ponimau-mir-mir-ponimaet-mena"),
            SchoolTeacherEntry("Варлашкина Е.А.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/varlaskina-e-a"),
            SchoolTeacherEntry("Войлокова И.А.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/vojlokova-i-a"),
            SchoolTeacherEntry("Дорощенко С.Г.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/doroseno-s-g"),
            SchoolTeacherEntry("Екимова О.М.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/ekimova-olga-mihajlovna"),
            SchoolTeacherEntry("Ефимова С.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/letova-s-v"),
            SchoolTeacherEntry("Иванова Е.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/ivanova-ev"),
            SchoolTeacherEntry("Каркалайнен И.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/nikolaeva-n-v"),
            SchoolTeacherEntry("Кудрявцева С.И.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/ilina-s-i"),
            SchoolTeacherEntry("Леднева О.Ю.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/ledneva-o-u"),
            SchoolTeacherEntry("Михайловская А.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/mihajlovskaa-a-v"),
            SchoolTeacherEntry("Панфилова Н.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/panfilova-n-v"),
            SchoolTeacherEntry("Погребная Ю.С.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/pogrebnaa-u-s"),
            SchoolTeacherEntry("Стефановская А.Р.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/stefanovskaa-a-r"),
            SchoolTeacherEntry("Тимофеева А.И.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/avakan-m-u"),
            SchoolTeacherEntry("Шелухина А.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-inostrannyh-azykov/seluhina-a-v")
        )),
        SchoolSubjectEntry("Обществознание", listOf(
            SchoolTeacherEntry("Бершадская Е.Л.", "https://sites.google.com/site/ucitelskijklub196/kafedra-istorii-i-obsestvoznania/bersadskaa-e-l-obsestvoznanie"),
            SchoolTeacherEntry("Кашина Н.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-istorii-i-obsestvoznania/kasina-n-v"),
            SchoolTeacherEntry("Моисенкова А.Р.")
        )),
        SchoolSubjectEntry("История", listOf(
            SchoolTeacherEntry("Ковалькова М.А.", "https://sites.google.com/site/ucitelskijklub196/kafedra-istorii-i-obsestvoznania/kovalkova-m-a", extra = "Ссылка на оформление доклада: https://docs.google.com/document/d/0B-nQgdNmGcNQUlRXVkJTUUZhUU0/edit"),
            SchoolTeacherEntry("Шумилова Е.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-istorii-i-obsestvoznania/sumilova-e-v-istoria", extra = "Директор гимназии"),
            SchoolTeacherEntry("Бершадская Е.Л.", "https://sites.google.com/site/ucitelskijklub196/kafedra-istorii-i-obsestvoznania/bersadskaa-e-l-obsestvoznanie"),
            SchoolTeacherEntry("Кашина Н.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-istorii-i-obsestvoznania/kasina-n-v"),
            SchoolTeacherEntry("Моисенкова А.Р.")
        )),
        SchoolSubjectEntry("ОбЖ и Физ-Культура", listOf(
            SchoolTeacherEntry("Коротченкова С.В.", "https://sites.google.com/site/ucitelskijklub196/%D1%84%D0%B8%D0%B7%D0%BA%D1%83%D0%BB%D1%8C%D1%82%D1%83%D1%80%D0%B0-%D0%B8-%D0%BE%D0%B1%D0%B6%D1%80/korotcenkova-s-v"),
            SchoolTeacherEntry("Латура А.В.", "https://sites.google.com/site/ucitelskijklub196/%D1%84%D0%B8%D0%B7%D0%BA%D1%83%D0%BB%D1%8C%D1%82%D1%83%D1%80%D0%B0-%D0%B8-%D0%BE%D0%B1%D0%B7%D1%80/latura-a-v"),
            SchoolTeacherEntry("Максимова С.А.", "https://sites.google.com/site/ucitelskijklub196/%D1%84%D0%B8%D0%B7%D0%BA%D1%83%D0%BB%D1%8C%D1%82%D1%83%D1%80%D0%B0-%D0%B8-%D0%BE%D0%B1%D0%B7%D1%80/maksimova-s-a"),
            SchoolTeacherEntry("Мохова К.Б.", "https://sites.google.com/site/ucitelskijklub196/%D1%84%D0%B8%D0%B7%D0%BA%D1%83%D0%BB%D1%8C%D1%82%D1%83%D1%80%D0%B0-%D0%B8-%D0%BE%D0%B1%D0%B7%D1%80/mohova-k-b")
        )),
        SchoolSubjectEntry("Физика и Химия", listOf(
            SchoolTeacherEntry("Чернышова Т.Н.", "https://sites.google.com/site/ucitelskijklub196/%D1%84%D0%B8%D0%B7%D0%B8%D0%BA%D0%B0/cernyseva-t-n"),
            SchoolTeacherEntry("Катунцева А.В."),
            SchoolTeacherEntry("Сафина Ю.Е."),
            SchoolTeacherEntry("Сажина Е.Г.", "https://sites.google.com/site/ucitelskijklub196/%D1%85%D0%B8%D0%BC%D0%B8%D1%8F/sazina-e-g")
        )),
        SchoolSubjectEntry("Биология и География", listOf(
            SchoolTeacherEntry("Александрова Е.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-estestvoznania/aleksandrova-e-v-geografia", extra = "Заместитель директора"),
            SchoolTeacherEntry("Сангаджиева К.Н.", "https://sites.google.com/site/ucitelskijklub196/kafedra-estestvoznania/sangadzieva-k-n"),
            SchoolTeacherEntry("Степанова С.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-estestvoznania/stepanova-s-v", extra = "Заместитель директора"),
            SchoolTeacherEntry("Ярина О.Г.", "https://sites.google.com/view/678196/%D0%B3%D0%BB%D0%B0%D0%B2%D0%BD%D0%B0%D1%8F")
        )),
        SchoolSubjectEntry("Информатика", listOf(
            SchoolTeacherEntry("Крутоверцева А.В.", "https://sites.google.com/site/ucitelskijklub196/%D0%B8%D0%BD%D1%84%D0%BE%D1%80%D0%BC%D0%B0%D1%82%D0%B8%D0%BA%D0%B0/informatika-mladsie-klassy"),
            SchoolTeacherEntry("Реуцкая Е.А.")
        )),
        SchoolSubjectEntry("Изо и Музыка", listOf(
            SchoolTeacherEntry("Горбачева Е.В.", "https://sites.google.com/site/ucitelskijklub196/kafedra-esteticeskogo-vospitania/gorbaceva-e-v---muzyka"),
            SchoolTeacherEntry("Бакланова О.Е.", "https://sites.google.com/site/ucitelskijklub196/kafedra-esteticeskogo-vospitania/baklanova-o-e"),
            SchoolTeacherEntry("Дрюма Д.В.")
        )),
        SchoolSubjectEntry("Технология", listOf(
            SchoolTeacherEntry("Хомченкова И.Б.", "https://sites.google.com/site/ucitelskijklub196/kafedra-matematiki/homcenko-i-a")
        )),
        SchoolSubjectEntry("Начальная школа", listOf(
            SchoolTeacherEntry("Андреева Н.Е."),
            SchoolTeacherEntry("Белова Ю.В."),
            SchoolTeacherEntry("Бучиц О.В."),
            SchoolTeacherEntry("Ваганова В.В."),
            SchoolTeacherEntry("Данилова Т.В."),
            SchoolTeacherEntry("Костью Е.Ю."),
            SchoolTeacherEntry("Кайялайнен Л.И."),
            SchoolTeacherEntry("Лобанова Л.М."),
            SchoolTeacherEntry("Лоборева М.В."),
            SchoolTeacherEntry("Михалева И.В."),
            SchoolTeacherEntry("Мякота В.А."),
            SchoolTeacherEntry("Подзолкина Т.А."),
            SchoolTeacherEntry("Полуянова Е.А."),
            SchoolTeacherEntry("Попова О.Н."),
            SchoolTeacherEntry("Ретровская Е.А."),
            SchoolTeacherEntry("Филина Т.Б."),
            SchoolTeacherEntry("Любарская Е.", extra = "логопед")
        ))
    )

    val BELL_SCHEDULE: List<Pair<String, String>> = listOf(
        "1 Урок" to "8:30 - 9:15",
        "2 Урок" to "9:25 - 10:10",
        "3 Урок" to "10:30 - 11:15",
        "4 Урок" to "11:35 - 12:20",
        "5 Урок" to "12:40 - 13:25",
        "6 Урок" to "13:35 - 14:20",
        "7 Урок" to "14:30 - 15:15"
    )
}

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int
)

val SCHOOL_QUIZ_QUESTIONS: List<QuizQuestion> = listOf(
    QuizQuestion("Сколько планет в Солнечной системе?", listOf("7", "8", "9", "10"), 1),
    QuizQuestion("Какой химический символ золота?", listOf("Go", "Gd", "Au", "Ag"), 2),
    QuizQuestion("В каком году была основана Россия (образование Древнерусского государства)?", listOf("862", "988", "1147", "1240"), 0),
    QuizQuestion("Чему равна сумма углов треугольника?", listOf("90°", "180°", "270°", "360°"), 1),
    QuizQuestion("Какой орган вырабатывает инсулин?", listOf("Печень", "Почки", "Поджелудочная железа", "Селезёнка"), 2),
    QuizQuestion("Автор романа «Война и мир»?", listOf("Достоевский", "Тургенев", "Толстой", "Чехов"), 2),
    QuizQuestion("Какая планета самая большая в Солнечной системе?", listOf("Сатурн", "Нептун", "Юпитер", "Уран"), 2),
    QuizQuestion("Скорость света (приближённо)?", listOf("200 000 км/с", "300 000 км/с", "400 000 км/с", "150 000 км/с"), 1),
    QuizQuestion("Какой газ больше всего в атмосфере Земли?", listOf("Кислород", "Углекислый газ", "Аргон", "Азот"), 3),
    QuizQuestion("Как звали первого человека в космосе?", listOf("Титов", "Гагарин", "Терешкова", "Леонов"), 1),
    QuizQuestion("Чему равно π (пи) приближённо?", listOf("2.14", "3.14", "4.14", "1.14"), 1),
    QuizQuestion("Столица Франции?", listOf("Берлин", "Лондон", "Рим", "Париж"), 3),
    QuizQuestion("Сколько хромосом у человека?", listOf("23", "44", "46", "48"), 2),
    QuizQuestion("Какой элемент имеет атомный номер 1?", listOf("Гелий", "Водород", "Литий", "Углерод"), 1),
    QuizQuestion("В каком веке произошла Отечественная война 1812 года?", listOf("XVIII", "XIX", "XX", "XVII"), 1),
    QuizQuestion("Что изучает биология?", listOf("Землю", "Живые организмы", "Звёзды", "Химические реакции"), 1),
    QuizQuestion("Сколько нот в музыкальной гамме?", listOf("5", "6", "7", "8"), 2),
    QuizQuestion("Самая длинная река в мире?", listOf("Амазонка", "Нил", "Янцзы", "Миссисипи"), 1),
    QuizQuestion("Формула воды?", listOf("HO", "H2O", "H3O", "OH2"), 1),
    QuizQuestion("Кто написал «Евгения Онегина»?", listOf("Лермонтов", "Гоголь", "Пушкин", "Некрасов"), 2)
)

data class SchoolFaqItem(
    val question: String,
    val answer: String
)

/**
 * FAQ адаптирован из бота под приложение: убраны упоминания Telegram-команд,
 * пути описаны через раздел «Школа» в настройках мессенджера.
 */
val SCHOOL_FAQ: List<SchoolFaqItem> = listOf(
    SchoolFaqItem(
        "Как посмотреть расписание уроков?",
        "Раздел «Школа» → «Расписание и звонки» → кнопка «Расписание уроков». " +
            "Откроется таблица в Google Drive."
    ),
    SchoolFaqItem(
        "Как посмотреть расписание звонков?",
        "Раздел «Школа» → «Расписание и звонки». Там же — обратный отсчёт до каникул."
    ),
    SchoolFaqItem(
        "Как узнать кабинет или сайт нужного учителя?",
        "Раздел «Школа» → «Учителя» → выберите предмет → выберите учителя. " +
            "В карточке будет ссылка на персональную страницу, если она есть."
    ),
    SchoolFaqItem(
        "Где смотреть новости гимназии?",
        "Раздел «Школа» → «Новости». Закреплённая новость (со значком 📌) " +
            "всегда показывается первой."
    ),
    SchoolFaqItem(
        "Как проголосовать в опросе?",
        "Раздел «Школа» → «Опросы» → выберите вариант. " +
            "Проголосовать можно только один раз, результаты видны сразу после голосования."
    ),
    SchoolFaqItem(
        "Как сыграть в игру «Камень, ножницы, бумага»?",
        "Раздел «Школа» → «Игра». Счёт сессии показывается в заголовке, " +
            "все победы сохраняются в вашем профиле раздела."
    ),
    SchoolFaqItem(
        "Как проходит викторина?",
        "Раздел «Школа» → «Викторина». 20 вопросов по школьной программе, " +
            "после каждого ответа показывается правильный вариант. " +
            "Счёт «Правильных: X из Y» сохраняется между запусками."
    ),
    SchoolFaqItem(
        "Как узнать, сколько дней до каникул?",
        "Раздел «Школа» → «Расписание и звонки» → карточка «До каникул»."
    ),
    SchoolFaqItem(
        "Как предложить идею для раздела «Школа»?",
        "Раздел «Школа» → «Оценка и идеи» → «Предложить идею». " +
            "Напишите вашу идею — она уйдёт разработчикам."
    ),
    SchoolFaqItem(
        "Как оценить раздел «Школа»?",
        "Раздел «Школа» → «Оценка и идеи». Поставьте от 1 до 5 звёзд, " +
            "при желании напишите, что понравилось и что не понравилось."
    ),
    SchoolFaqItem(
        "Где найти контакты гимназии для родителей?",
        "Раздел «Школа» → «Родителям»: адрес, телефон, почта, сайт и группа ВКонтакте."
    ),
    SchoolFaqItem(
        "Как скрыть ненужные подразделы «Школы»?",
        "Настройки → Аккаунт → «Настройки раздела „Школа“». Там можно выключить " +
            "как весь раздел, так и отдельные подразделы — например, игру или викторину."
    ),
    SchoolFaqItem(
        "Что такое страница учителя?",
        "У каждого учителя есть страница внутри приложения: файл урока, вопросы " +
            "учеников и подписка на обновления файла. Открыть её можно из раздела " +
            "«Школа» → «Учителя» → выберите учителя → «Страница учителя»."
    ),
    SchoolFaqItem(
        "Как учителю получить свою страницу?",
        "Администратор создаёт профиль учителя и привязывает его к аккаунту " +
            "мессенджера. После этого включите «Режим учителя» в настройках раздела — " +
            "в разделе «Школа» появится «Моя страница учителя»."
    ),
    SchoolFaqItem(
        "Как учителю обновить файл урока?",
        "«Школа» → «Моя страница учителя» → «Обновить файл урока». Вставьте ссылку " +
            "(Google Drive и т.п.) и описание — все подписчики увидят обновление."
    ),
    SchoolFaqItem(
        "Как задать вопрос учителю?",
        "Откройте страницу учителя (Школа → Учителя → учитель → «Страница учителя») " +
            "и нажмите «Задать вопрос учителю». Учитель может скрыть вопрос — " +
            "тогда его будете видеть только вы двое."
    ),
    SchoolFaqItem(
        "Учитель ответит на мой вопрос?",
        "Да — рядом с вопросом появится блок «Ответ учителя», а пометка «ждёт " +
            "ответа» исчезнет. Учитель получает уведомление о новом вопросе и может " +
            "изменить или дополнить свой ответ позже."
    ),
    SchoolFaqItem(
        "Как учителю ответить на вопрос?",
        "«Школа» → «Моя страница учителя» → карточка вопроса → «Ответить». " +
            "Сверху списка вопросов показывается, сколько вопросов ждут ответа; " +
            "кнопка «Изменить ответ» доступна под каждым уже отвеченным вопросом."
    ),
    SchoolFaqItem(
        "Где посмотреть предыдущие файлы урока?",
        "На странице учителя, под текущим файлом, есть блок «Предыдущие файлы» — " +
            "до трёх последних версий с датами. Каждый можно открыть одной кнопкой."
    )
)
