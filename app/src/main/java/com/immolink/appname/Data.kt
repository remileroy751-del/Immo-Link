package com.immolink.appname

object Data {
    val countries = listOf("Togo" to "00228", "Bénin" to "00229", "Mali" to "00223", "Burkina Faso" to "00226", "Côte d'Ivoire" to "00225")
    val countryIso = mapOf("Togo" to "TG", "Bénin" to "BJ", "Mali" to "ML", "Burkina Faso" to "BF", "Côte d'Ivoire" to "CI")
    val cities = mapOf(
        "Togo" to listOf("Lomé", "Sokodé", "Kara", "Atakpamé", "Dapaong", "Tsévié", "Kpalimé", "Notsé", "Aného", "Bassar", "Bafilo", "Amlamé", "Badou", "Niamtougou", "Mango", "Vogan", "Tabligbo", "Tchamba", "Blitta", "Kévé", "Agou-Gadzépé", "Assahoun"),
        "Bénin" to listOf("Cotonou", "Porto-Novo", "Abomey-Calavi", "Parakou", "Djougou", "Bohicon", "Abomey", "Natitingou", "Ouidah", "Lokossa", "Kandi", "Savalou", "Sakété", "Comè", "Malanville", "Nikki", "Allada", "Aplahoué", "Dogbo", "Kétou", "Bembèrèkè", "Savè", "Tanguiéta", "Bassila"),
        "Mali" to listOf("Bamako", "Sikasso", "Mopti", "Ségou", "Kayes", "Koulikoro", "Gao", "Tombouctou", "Kidal", "Kati", "San", "Nioro du Sahel", "Kita", "Bougouni", "Koutiala", "Markala", "Niono", "Fana", "Banamba", "Bandiagara", "Douentza", "Diré", "Ansongo", "Yorosso"),
        "Burkina Faso" to listOf("Ouagadougou", "Bobo-Dioulasso", "Koudougou", "Banfora", "Ouahigouya", "Fada N'Gourma", "Dédougou", "Tenkodogo", "Kaya", "Ziniaré", "Gaoua", "Dori", "Manga", "Réo", "Houndé", "Diapaga", "Koupéla", "Pouytenga", "Kongoussi", "Djibo", "Gorom-Gorom", "Orodara", "Solenzo"),
        "Côte d'Ivoire" to listOf("Abidjan", "Bouaké", "Yamoussoukro", "Daloa", "Korhogo", "San-Pédro", "Man", "Gagnoa", "Abengourou", "Divo", "Grand-Bassam", "Anyama", "Agboville", "Bondoukou", "Séguéla", "Odienné", "Ferkessédougou", "Dimbokro", "Daoukro", "Aboisso", "Adzopé", "Issia", "Soubré", "Duékoué", "Guiglo", "Sinfra", "Toumodi", "Tiassalé", "Dabou", "Bingerville")
    )
    val saleTypes = listOf("Terrain nu", "Terrain bâti", "Maison", "Villa", "Immeuble")
    val rentTypes = listOf("Une chambre simple", "Une chambre + WC et douche interne", "Une chambre salon", "Une chambre salon + WC douche interne", "3 chambres salon + WC et douche interne", "Une maison", "Une villa", "Un bureau", "Un terrain")
    val relationships = listOf("Je suis le propriétaire", "Je suis en contact avec le propriétaire", "Je ne connais pas le propriétaire", "Je suis démarcheur")
    val rentBudgets = listOf(BudgetRange("Moins de 50 000 FCFA", max = 50_000), BudgetRange("50 000 à 100 000 FCFA", 50_000, 100_000), BudgetRange("100 000 à 150 000 FCFA", 100_000, 150_000), BudgetRange("150 000 à 250 000 FCFA", 150_000, 250_000), BudgetRange("Plus de 250 000 FCFA", 250_000))
    val saleBudgets = listOf(BudgetRange("Moins de 5 000 000 FCFA", max = 5_000_000), BudgetRange("5 à 15 millions FCFA", 5_000_000, 15_000_000), BudgetRange("15 à 30 millions FCFA", 15_000_000, 30_000_000), BudgetRange("30 à 60 millions FCFA", 30_000_000, 60_000_000), BudgetRange("Plus de 60 millions FCFA", 60_000_000))
}
