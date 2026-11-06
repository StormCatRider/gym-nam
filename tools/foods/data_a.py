# Rows: English | Spanish | emoji (blank = category default) | kcal | protein g | carbs g | fat g  (all per 100 g)
#       | serving code | grams in that serving
# Values are standard reference figures (USDA-style) rounded; restaurant/home dishes are typical estimates.

FRUITS = ("🍎", """
Apple|Manzana|🍎|52|0.3|14|0.2|md|182
Apple, large|Manzana grande|🍎|52|0.3|14|0.2|lg|223
Green apple|Manzana verde|🍏|58|0.4|14|0.2|md|182
Banana|Plátano|🍌|89|1.1|23|0.3|md|118
Banana, small|Plátano chico|🍌|89|1.1|23|0.3|sm|101
Banana, large|Plátano grande|🍌|89|1.1|23|0.3|lg|136
Orange|Naranja|🍊|47|0.9|12|0.1|md|131
Mandarin|Mandarina|🍊|53|0.8|13|0.3|md|88
Clementine|Clementina|🍊|47|0.9|12|0.2|md|74
Grapefruit|Toronja||42|0.8|11|0.1|hlf|123
Lemon|Limón amarillo|🍋|29|1.1|9|0.3|md|58
Lime|Limón|🍋|30|0.7|11|0.2|md|67
Strawberries|Fresas|🍓|32|0.7|7.7|0.3|cup|152
Blueberries|Moras azules|🫐|57|0.7|14|0.3|cup|148
Raspberries|Frambuesas||52|1.2|12|0.7|cup|123
Blackberries|Zarzamoras||43|1.4|10|0.5|cup|144
Frozen mixed berries|Frutos rojos congelados|🍓|48|0.7|12|0.3|cup|140
Cranberries, fresh|Arándanos rojos frescos||46|0.5|12|0.1|cup|100
Grapes|Uvas|🍇|69|0.7|18|0.2|cup|151
Watermelon|Sandía|🍉|30|0.6|7.6|0.2|cup|152
Cantaloupe|Melón|🍈|34|0.8|8.2|0.2|cup|160
Honeydew melon|Melón verde|🍈|36|0.5|9.1|0.1|cup|170
Pineapple|Piña|🍍|50|0.5|13|0.1|cup|165
Pineapple, canned in juice|Piña en lata (en jugo)|🍍|60|0.4|16|0.1|hcup|125
Mango|Mango|🥭|60|0.8|15|0.4|md|200
Papaya|Papaya||43|0.5|11|0.3|cup|145
Peach|Durazno|🍑|39|0.9|10|0.3|md|150
Nectarine|Nectarina|🍑|44|1.1|11|0.3|md|142
Plum|Ciruela||46|0.7|11|0.3|md|66
Apricot|Chabacano||48|1.4|11|0.4|md|35
Cherries|Cerezas|🍒|63|1.1|16|0.2|cup|154
Maraschino cherry|Cereza en almíbar|🍒|165|0.2|42|0.2|pc|5
Pear|Pera|🍐|57|0.4|15|0.1|md|178
Kiwi|Kiwi|🥝|61|1.1|15|0.5|md|69
Pomegranate seeds|Granada (semillas)||83|1.7|19|1.2|hcup|87
Guava|Guayaba||68|2.6|14|1|md|55
Passion fruit|Maracuyá||97|2.2|23|0.7|md|18
Coconut meat, fresh|Coco fresco|🥥|354|3.3|15|33|hcup|40
Avocado|Aguacate|🥑|160|2|8.5|15|md|150
Persimmon|Caqui (persimón)||70|0.6|19|0.2|md|168
Lychee|Lichi||66|0.8|17|0.4|cup|190
Dragon fruit|Pitaya||60|1.2|13|0|cup|227
Star fruit|Carambola||31|1|6.7|0.3|md|91
Mamey|Mamey||124|1.5|32|0.5|cup|175
Prickly pear|Tuna (fruta)||41|0.7|10|0.5|md|103
Soursop|Guanábana||66|1|17|0.3|cup|225
Cherimoya|Chirimoya||75|1.6|18|0.7|cup|160
Nance|Nanche||73|0.7|17|1.2|cup|112
Plantain, fried (ripe)|Plátano macho frito||236|1.5|38|9|srv|100
Plantain, boiled|Plátano macho hervido||116|0.8|31|0.2|cup|154
Fruit salad|Ensalada de frutas||50|0.6|13|0.2|cup|150
Applesauce, unsweetened|Puré de manzana sin azúcar|🍎|42|0.2|11|0.1|hcup|122
Fruit cocktail in syrup|Coctel de frutas en almíbar||73|0.4|19|0.1|hcup|124
Peaches in syrup|Duraznos en almíbar|🍑|74|0.5|20|0|hcup|128
Mandarin oranges, canned|Gajos de mandarina en lata|🍊|61|0.5|16|0.1|hcup|126
Raisins|Pasas||299|3.1|79|0.5|oz|28
Dried cranberries|Arándanos deshidratados||308|0.2|82|1.1|oz|28
Prunes|Ciruelas pasa||240|2.2|64|0.4|oz|28
Dried mango|Mango deshidratado|🥭|319|2.5|78|1.2|oz|28
Dried apricots|Chabacanos deshidratados||241|3.4|63|0.5|oz|28
Dried figs|Higos secos||249|3.3|64|0.9|oz|28
Figs, fresh|Higos frescos||74|0.8|19|0.3|md|50
Dates, Medjool|Dátiles Medjool||277|1.8|75|0.2|pc|24
Dates, Deglet Noor|Dátiles Deglet Noor||282|2.5|75|0.4|pc|7
Banana chips|Chips de plátano|🍌|519|2.3|58|34|oz|28
Acai purée, unsweetened|Pulpa de açaí sin azúcar||70|1|4|5|pk|100
""")

VEGETABLES = ("🥦", """
Broccoli, raw|Brócoli crudo|🥦|34|2.8|6.6|0.4|cup|91
Broccoli, cooked|Brócoli cocido|🥦|35|2.4|7.2|0.4|cup|156
Cauliflower, raw|Coliflor cruda||25|1.9|5|0.3|cup|107
Cauliflower, cooked|Coliflor cocida||23|1.8|4.1|0.5|cup|124
Cauliflower rice|Arroz de coliflor||25|1.9|5|0.3|cup|107
Carrot, raw|Zanahoria cruda|🥕|41|0.9|10|0.2|md|61
Carrots, cooked|Zanahoria cocida|🥕|35|0.8|8.2|0.2|cup|156
Baby carrots|Zanahorias baby|🥕|35|0.6|8.2|0.1|srv|85
Spinach, raw|Espinaca cruda|🥬|23|2.9|3.6|0.4|cup|30
Spinach, cooked|Espinaca cocida|🥬|23|3|3.8|0.3|cup|180
Kale, raw|Kale crudo|🥬|35|2.9|4.4|1.5|cup|21
Kale, cooked|Kale cocido|🥬|36|3|5.6|0.5|cup|130
Romaine lettuce|Lechuga romana|🥬|17|1.2|3.3|0.3|cup|47
Iceberg lettuce|Lechuga orejona|🥬|14|0.9|3|0.1|cup|72
Butter lettuce|Lechuga mantequilla|🥬|13|1.4|2.2|0.2|cup|55
Mixed greens|Mezcla de hojas verdes|🥬|20|1.5|3.5|0.3|cup|35
Arugula|Arúgula|🥬|25|2.6|3.7|0.7|cup|20
Watercress|Berros|🥬|11|2.3|1.3|0.1|cup|34
Cabbage, green|Col / repollo|🥬|25|1.3|5.8|0.1|cup|89
Red cabbage|Col morada|🥬|31|1.4|7.4|0.2|cup|89
Bok choy, cooked|Bok choy cocido|🥬|12|1.6|1.8|0.2|cup|170
Collard greens, cooked|Berza cocida|🥬|33|2.7|5.7|0.7|cup|190
Swiss chard, cooked|Acelga cocida|🥬|20|1.9|4.1|0.1|cup|175
Purslane|Verdolagas|🥬|20|2|3.4|0.4|cup|115
Cucumber|Pepino|🥒|15|0.7|3.6|0.1|cup|104
Tomato|Jitomate|🍅|18|0.9|3.9|0.2|md|123
Cherry tomatoes|Jitomates cherry|🍅|18|0.9|3.9|0.2|cup|149
Tomatillo|Tomate verde||32|1|5.8|1|md|34
Tomato sauce, canned|Puré de tomate (lata)|🍅|24|1.2|5.3|0.2|hcup|122
Diced tomatoes, canned|Jitomate en cubos (lata)|🍅|24|0.8|4.7|0.2|hcup|121
Tomato paste|Pasta de tomate|🍅|82|4.3|19|0.5|tbsp|16
Sun-dried tomatoes|Jitomates deshidratados|🍅|258|14|56|3|oz|28
Bell pepper, red|Pimiento morrón rojo|🫑|31|1|6|0.3|md|119
Bell pepper, green|Pimiento morrón verde|🫑|20|0.9|4.6|0.2|md|119
Bell pepper, yellow|Pimiento morrón amarillo|🫑|27|1|6.3|0.2|md|186
Jalapeño pepper|Chile jalapeño|🌶️|29|0.9|6.5|0.4|pc|14
Serrano pepper|Chile serrano|🌶️|32|1.7|6.7|0.4|pc|6
Poblano pepper|Chile poblano|🫑|20|0.9|4.6|0.2|md|120
Onion|Cebolla|🧅|40|1.1|9.3|0.1|md|110
Green onion|Cebollín|🧅|32|1.8|7.3|0.2|pc|15
Garlic|Ajo|🧄|149|6.4|33|0.5|clv|3
Ginger|Jengibre||80|1.8|18|0.8|tsp|2
Cilantro|Cilantro|🌿|23|2.1|3.7|0.5|tbsp|1
Parsley|Perejil|🌿|36|3|6.3|0.8|tbsp|4
Basil, fresh|Albahaca fresca|🌿|23|3.2|2.7|0.6|tbsp|3
Zucchini|Calabacita||17|1.2|3.1|0.3|md|196
Yellow squash|Calabaza amarilla||16|1.2|3.4|0.2|md|196
Butternut squash, cooked|Calabaza butternut cocida||40|0.9|10|0.1|cup|205
Pumpkin, cooked|Calabaza de Castilla cocida|🎃|20|0.7|4.9|0.1|cup|245
Spaghetti squash, cooked|Calabaza espagueti cocida||27|0.7|6.5|0.3|cup|155
Squash blossoms|Flor de calabaza|🌼|15|1|3|0.1|cup|134
Eggplant, cooked|Berenjena cocida|🍆|35|0.8|8.7|0.2|cup|99
Mushrooms, raw|Champiñones crudos|🍄|22|3.1|3.3|0.3|cup|70
Mushrooms, cooked|Champiñones cocidos|🍄|28|2.2|5.3|0.5|cup|156
Portobello, grilled|Hongo portobello asado|🍄|29|3.3|4.4|0.6|pc|121
Asparagus, cooked|Espárragos cocidos||22|2.4|4.1|0.2|cup|180
Green beans, cooked|Ejotes cocidos||35|1.9|7.9|0.3|cup|125
Green beans, canned|Ejotes de lata||20|1|4.5|0.1|hcup|68
Peas, cooked|Chícharos cocidos||84|5.4|16|0.2|cup|160
Peas and carrots|Chícharos con zanahoria||48|3|10|0.4|hcup|80
Snow peas|Chícharos chinos||42|2.8|7.6|0.2|cup|98
Sweet corn, cooked|Elote desgranado cocido|🌽|96|3.4|21|1.5|cup|164
Corn, canned|Elote de lata|🌽|64|2|14|1|hcup|82
Corn on the cob|Elote (mazorca)|🌽|96|3.4|21|1.5|md|103
Celery|Apio||16|0.7|3|0.2|stk|40
Radish|Rábano||16|0.7|3.4|0.1|pc|5
Beets, cooked|Betabel cocido||44|1.7|10|0.2|cup|170
Brussels sprouts, cooked|Coles de Bruselas cocidas||36|2.6|7.1|0.5|cup|156
Artichoke, cooked|Alcachofa cocida||53|2.9|12|0.3|md|120
Okra, cooked|Okra cocida||22|1.9|4.5|0.2|cup|160
Leeks, cooked|Poro cocido||31|0.8|7.6|0.2|cup|104
Turnip, cooked|Nabo cocido||22|0.7|5.1|0.1|cup|156
Parsnip, cooked|Chirivía cocida||71|1.3|17|0.3|cup|156
Jicama|Jícama||38|0.7|9|0.1|cup|130
Nopales, cooked|Nopales cocidos|🌵|15|1.4|3.3|0.1|cup|149
Chayote, cooked|Chayote cocido||24|0.6|5.1|0.5|cup|160
Bean sprouts|Germen de soya||30|3|5.9|0.2|cup|104
Hearts of palm|Palmitos||28|2.5|4.6|0.6|hcup|73
Sauerkraut|Chucrut||19|0.9|4.3|0.1|hcup|71
Pickle, dill|Pepinillo||12|0.5|2.4|0.2|pc|35
Olives, black|Aceitunas negras|🫒|115|0.8|6|11|pc|4
Olives, green|Aceitunas verdes|🫒|145|1|3.8|15|pc|3
Mixed vegetables, cooked|Verduras mixtas cocidas||65|2.9|13|0.2|cup|182
Roasted vegetables|Verduras asadas||73|1.7|8|3.8|cup|150
Stir-fried vegetables|Verduras salteadas||70|2|8|3.5|cup|150
Potato, baked|Papa horneada|🥔|93|2.5|21|0.1|md|173
Potato, boiled|Papa hervida|🥔|87|1.9|20|0.1|md|136
Sweet potato, baked|Camote horneado|🍠|90|2|21|0.2|md|114
Mashed potatoes|Puré de papa|🥔|113|1.9|16|4.2|cup|210
Hash browns|Papas hash brown|🥔|265|2.6|35|13|srv|100
Potato wedges, oven|Papas gajo al horno|🥔|152|2.5|22|6|cup|130
Sweet potato fries|Papas de camote fritas|🍠|268|2.5|33|14|srv|100
Tater tots|Croquetas de papa (tater tots)|🥔|221|2.5|28|11|srv|86
Coleslaw|Ensalada de col||150|1|14|10|hcup|95
Plant-based burger patty|Hamburguesa vegetal|🌱|204|18|4.4|12.4|pc|113
""")

MEAT = ("🥩", """
Chicken breast, grilled|Pechuga de pollo a la plancha|🍗|165|31|0|3.6|oz4|113
Chicken breast, raw|Pechuga de pollo cruda|🍗|120|22.5|0|2.6|oz4|113
Chicken thigh, skinless, cooked|Muslo de pollo sin piel cocido|🍗|179|24.8|0|8.2|oz3|85
Chicken thigh with skin, roasted|Muslo de pollo con piel asado|🍗|229|23|0|15|pc|62
Chicken drumstick with skin, roasted|Pierna de pollo con piel asada|🍗|191|23.4|0|10.2|pc|52
Chicken wings, roasted|Alitas de pollo asadas|🍗|254|23.8|0|16.9|pc|34
Whole chicken with skin, roasted|Pollo entero con piel asado|🍗|239|27|0|14|oz4|113
Rotisserie chicken|Pollo rostizado|🍗|215|25|0|12|oz3|85
Fried chicken breast, breaded|Pechuga de pollo frita empanizada|🍗|260|25|9|14|pc|140
Chicken nuggets|Nuggets de pollo|🍗|296|15|16|19|pc|16
Ground chicken, cooked|Pollo molido cocido|🍗|189|23|0|10|oz4|113
Chicken breast, deli sliced|Pechuga de pollo rebanada|🍗|102|18|3|2|oz2|56
Canned chicken|Pollo en lata|🍗|118|25|0|2|oz2|56
Chicken sausage|Salchicha de pollo|🌭|165|16|5|9|pc|85
Turkey breast, roasted|Pechuga de pavo asada|🦃|135|30|0|0.7|oz4|113
Turkey deli slices|Jamón de pavo rebanado|🦃|104|17|4|2|oz|28
Ground turkey 93%, cooked|Pavo molido 93% cocido|🦃|213|27.1|0|11.6|oz4|113
Ground turkey 93%, raw|Pavo molido 93% crudo|🦃|150|19|0|8.3|oz4|113
Ground turkey 85%, cooked|Pavo molido 85% cocido|🦃|258|25|0|17|oz4|113
Turkey leg, smoked|Pierna de pavo ahumada|🦃|202|28|0|10|oz4|113
Turkey bacon|Tocino de pavo|🥓|368|30|4|26|sl|12
Turkey sausage (Mexican style)|Salchicha de pavo|🌭|163|11|5|11|pc|30
Ground beef 80/20, cooked|Carne molida 80/20 cocida|🥩|272|27|0|17.4|oz4|113
Ground beef 80/20, raw|Carne molida 80/20 cruda|🥩|254|17|0|20|oz4|113
Ground beef 85/15, cooked|Carne molida 85/15 cocida|🥩|256|27.7|0|15.3|oz4|113
Ground beef 90/10, cooked|Carne molida 90/10 cocida|🥩|230|28.4|0|12|oz4|113
Ground beef 90/10, raw|Carne molida 90/10 cruda|🥩|176|20|0|10|oz4|113
Ground beef 95/5, cooked|Carne molida 95/5 cocida|🥩|193|29.2|0|7.6|oz4|113
Hamburger patty (¼ lb raw), cooked|Carne para hamburguesa cocida|🥩|272|27|0|17.4|pc|85
Sirloin steak, grilled|Sirloin a la parrilla|🥩|212|29.3|0|9.7|oz4|113
Ribeye steak, cooked|Rib eye cocido|🥩|303|22.9|0|23.5|oz4|113
New York strip, cooked|Bistec New York cocido|🥩|240|27|0|14|oz4|113
Filet mignon, cooked|Filete mignon cocido|🥩|219|30|0|11|oz4|113
T-bone steak, cooked|T-bone cocido|🥩|239|26|0|15|oz4|113
Flank steak, cooked|Falda de res cocida|🥩|192|28|0|8|oz4|113
Beef brisket, cooked|Pecho de res (brisket) cocido|🥩|290|25|0|21|oz4|113
Beef chuck, braised|Espaldilla de res guisada|🥩|250|29|0|14|oz4|113
Beef short ribs, braised|Costilla corta de res|🥩|471|22|0|42|oz4|113
Pot roast|Asado de res (pot roast)|🥩|200|28|0|9|oz4|113
Roast beef, deli|Roast beef rebanado|🥩|117|18|0.6|4|oz2|56
Pastrami|Pastrami|🥩|142|21|1|6|oz2|56
Corned beef|Corned beef|🥩|245|18|0.5|19|oz3|85
Beef jerky|Carne seca (beef jerky)|🥩|410|33|11|25.6|oz|28
Beef liver, cooked|Hígado de res cocido|🥩|167|26|5.2|4.7|oz3|85
Meatballs, beef|Albóndigas de res||253|17|8|17|pc|30
Meatloaf|Pastel de carne (meatloaf)||200|14|9|12|sl|150
Pork chop, cooked|Chuleta de cerdo cocida|🥩|209|25.6|0|11.1|pc|145
Pork tenderloin, cooked|Lomo de cerdo cocido|🥩|143|26|0|3.5|oz4|113
Pork shoulder, roasted|Espaldilla de cerdo asada|🥩|230|23|0|15|oz4|113
Pork ribs, cooked (no sauce)|Costillas de cerdo (sin salsa)|🍖|356|26|0|28|oz4|113
Pork belly|Panceta de cerdo|🥓|518|9.3|0|53|oz|28
Bacon, cooked|Tocino cocido|🥓|541|37|1.4|42|sl|8
Canadian bacon|Tocino canadiense|🥓|144|24|1.8|4.6|sl|23
Ham, sliced|Jamón rebanado|🍖|163|16.6|3.8|8.6|sl|28
Pork sausage link, cooked|Salchicha de cerdo para desayuno|🌭|339|19|1|28|pc|25
Hot dog (frank only)|Salchicha para hot dog|🌭|290|10|4|26|pc|45
Chicken hot dog|Salchicha de pollo para hot dog|🌭|225|15|3|17|pc|45
Bratwurst|Salchicha bratwurst|🌭|294|12|3|26|pc|85
Italian sausage|Salchicha italiana|🌭|344|19|4.3|28|pc|83
Kielbasa|Salchicha polaca (kielbasa)|🌭|297|13|5|25|oz2|56
Vienna sausages|Salchichas viena (lata)|🌭|230|10|2.6|20|pc|16
Bologna|Mortadela (bologna)||312|11|4|28|sl|28
Salami|Salami||336|22|1.2|26|oz|28
Pepperoni|Pepperoni|🍕|504|19|1.2|46|oz|28
Prosciutto|Prosciutto||253|25|0|17|oz|28
Canned luncheon meat (Spam type)|Carne enlatada (tipo Spam)||321|13|4.6|27|sl|56
Lamb, roasted|Cordero asado||258|26|0|16.5|oz4|113
Goat meat, cooked|Carne de chivo cocida||143|27|0|3|oz4|113
Bison, ground, cooked|Bisonte molido cocido||179|25|0|8.6|oz4|113
""")
