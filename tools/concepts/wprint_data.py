"""Тексты и стили второго раунда концептов брони Странника (стили общие для всех раундов)."""

ROUND = 'Второй раунд'
LEDE = ('По первому раунду выбраны три облика, они ниже. Для R1, R4, R5 и R7 - по три новых варианта, буквы Г, Д, Е, чтобы не путать '
        'с первым раундом. R1 теперь делается из божественной брони, поэтому все три новых R1 идут от неё: золото, тёмно-алые вставки, '
        'голубой камень, стальные ботинки. Модели так же черновые: из блоков, на костях игрока, слева вид спереди, справа сзади.')

PICKED = [
    ('r2c', 2, 'Эдем', 'В', 'Лепестки и нимб', 'Золотые и алые лепестки на груди и плечах, золотой обруч с камнем, над головой светится нимб.'),
    ('r3b', 3, 'Дикий лес', 'Б', 'Деревянные латы', 'Латы из синего дерева, глухой шлем с ветвистыми рогами, корни у ботинок.'),
    ('r6a', 6, 'Мортум', 'А', 'Тень в капюшоне', 'Чёрный рваный плащ и капюшон, лицо в темноте, красные глаза, цепи на поясе.'),
]

TOC = [
    (1, 'Божественная', '#ccaf3b', 'новые'),
    (2, 'Эдем', '#d8401c', 'выбрано В'),
    (3, 'Дикий лес', '#1a62e0', 'выбрано Б'),
    (4, 'Апалачия', '#c020d8', 'новые'),
    (5, 'Небесный терн', '#aeb2ba', 'новые'),
    (6, 'Мортум', '#ff3020', 'выбрано А'),
    (7, 'Галит', '#50e010', 'новые'),
]

TIERS = [
    (1, 'Божественная', 'из божественной брони, без босса', ['#ccaf3b', '#a01c0c', '#46d6e6'], [
        ('r1d', 'Г', 'Путник в божественном', 'Тёмный дорожный кафтан, поверх золотая кираса с голубым камнем, золотые наплечники и наручи, '
                                             'обруч с камнем на лбу, короткий алый плащ, стальные ботинки как у божественной брони.'),
        ('r1e', 'Д', 'Божественные латы', 'Сама божественная броня, только тяжелее: золотые латы с алой каймой, алые кисти под наплечниками, '
                                         'глухой шлем с голубым камнем во лбу и алым гребнем, стальные ботинки.'),
        ('r1f', 'Е', 'Вестник', 'Светлые латы с золотом и алой перевязью, голубые камни на шлеме и груди, за спиной сложенные золотые '
                               'крылья: верх выглядывает над плечами, маховые перья почти до колен.')]),
    (4, 'Апалачия', 'босс - Великий кадиллион Апалачии', ['#c020d8', '#5a1078', '#f090ff'], [
        ('r4d', 'Г', 'Чародей', 'Тёмная мантия до пят со светящейся каймой и рунами по подолу, широкие рукава, '
                               'остроконечная шляпа с сиреневой лентой, за спиной парит руническое кольцо.'),
        ('r4e', 'Д', 'Зачарованный воин', 'Стальные латы с сиреневым отблеском зачарования, пурпурный табар с золотой каймой, '
                                         'шлем с длинным пурпурным плюмажем, за спиной призрачный круглый щит.'),
        ('r4f', 'Е', 'Горы Апалачии', 'Броня как скала: лиловый камень, пурпурный мох на воротнике, поясе и у краёв, '
                                     'на плечах и шлеме ступенчатые пики со снежными шапками.')]),
    (5, 'Небесный терн', 'босс - Великий кадиллион Небесного терна', ['#c8ccd4', '#1c2450', '#8ab8ec'], [
        ('r5d', 'Г', 'Мегалит', 'Тяжёлые каменные плиты со светящимися голубыми швами, огромные квадратные наплечники, '
                               'на груди светится круг, шлем-монолит с одной светящейся прорезью.'),
        ('r5e', 'Д', 'Звездочёт', 'Ночное небо: тёмно-синий капюшон и плащ в мерцающих звёздах с серебряной каймой, '
                                 'серп луны на груди, рядом парят маленькие звёзды.'),
        ('r5f', 'Е', 'Буревестник', 'Грозовые латы с молнией через грудь и шлем, на плечах тучи, из них вниз бьют молнии, '
                                   'шлем с птичьим клювом и гребнем из перьев.')]),
    (7, 'Галит', 'босс - Карот', ['#50e010', '#1e4a0a', '#e0b84a'], [
        ('r7d', 'Г', 'Венец странствий', 'Тёмно-зелёные латы с золотом. На груди вокруг светящегося галита - шесть камней пройденных миров, '
                                        'те же камни в золотой короне на шлеме, длинный плащ с золотой каймой.'),
        ('r7e', 'Д', 'Парящие латы', 'Пластины галита не касаются тела: висят вокруг тёмного костюма, между ними светятся швы, '
                                    'шлем из раздвинутых пластин со светящейся щелью, за спиной кольцо света.'),
        ('r7f', 'Е', 'Странник у цели', 'Длинный тёмно-зелёный сюртук со светящимися пуговицами, шарф из галита, широкополая шляпа '
                                       'с золотой лентой и зелёным пером, на поясе галитовый фонарь.')]),
]

CSS = """
@page { size: A4 portrait; margin: 0; }
* { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
html, body { margin: 0; background: #131116; color: #ece5da; font-family: "Golos Text", "Segoe UI", sans-serif; }
.page { width: 210mm; height: 297mm; padding: 12mm 12mm 10mm; page-break-after: always; break-after: page; display: flex; flex-direction: column; overflow: hidden; }
.page:last-child { page-break-after: auto; break-after: auto; }
h1, h2, h3 { font-family: "Pixelify Sans", "Courier New", monospace; font-weight: 700; margin: 0; letter-spacing: .02em; }
h1 { font-size: 30pt; line-height: 1.1; }
.sub { font-family: "Pixelify Sans", monospace; color: #e3a347; font-size: 15pt; margin-top: 1mm; }
h3 { font-size: 13pt; color: #e3a347; margin: 5mm 0 3mm; }
.lede { color: #a39aa6; font-size: 10.5pt; margin: 4mm 0 0; line-height: 1.45; }
.picked { display: flex; flex-direction: column; gap: 3mm; flex: 1; min-height: 0; }
.picked .card { flex: 1; }
.toc { margin-top: 5mm; display: grid; grid-template-columns: repeat(7, 1fr); gap: 3mm; }
.toc div { border-top: 1.2mm solid; padding-top: 2mm; font-size: 9.5pt; color: #a39aa6; }
.toc span { display: block; font-family: "Pixelify Sans", monospace; color: #ece5da; font-size: 13pt; }
.toc em { display: block; font-style: normal; color: #e3a347; font-size: 8.5pt; margin-top: 1mm; }
.head { display: flex; align-items: baseline; gap: 5mm; border-bottom: 1px solid #322c38; padding-bottom: 3mm; }
.head h2 { font-size: 22pt; }
.head .rank { color: #e3a347; }
.head .meta { color: #a39aa6; font-size: 10.5pt; }
.chips { display: inline-flex; gap: 1.5mm; }
.chips i { width: 4mm; height: 4mm; border-radius: .8mm; display: inline-block; border: 1px solid #00000066; }
.grid { display: flex; flex-direction: column; gap: 4mm; margin-top: 5mm; flex: 1; min-height: 0; }
.card { background: #1c1920; border: 1px solid #322c38; border-radius: 2mm; overflow: hidden; display: flex; flex: 1; min-height: 0; }
.pic { position: relative; height: 100%; aspect-ratio: 3 / 2; flex: none; }
.pic img { height: 100%; width: 100%; display: block; }
.views { position: absolute; top: 2mm; left: 3mm; right: 3mm; display: flex; justify-content: space-between; font-size: 7.5pt; letter-spacing: .08em; text-transform: uppercase; color: #ffffffa0; }
.body { padding: 5mm 5mm; min-width: 0; }
.title { font-weight: 600; font-size: 12.5pt; }
.title.t2 { margin-top: 1mm; }
.title .ln { font-family: "Pixelify Sans", monospace; color: #e3a347; margin-right: 2mm; }
.desc { color: #a39aa6; font-size: 10pt; line-height: 1.4; margin: 1.5mm 0 0; }
.foot { margin-top: 3mm; display: flex; justify-content: space-between; color: #6e6674; font-size: 8.5pt; }
"""
