package com.mcclabs.gimmer.data

object ExerciseMetadata {
    private val compoundExercises = setOf(
        "machine_chest_press", "lat_pulldown", "incline_db_press", "seated_cable_row",
        "hack_squat", "romanian_deadlift", "incline_smith_press", "assisted_dip",
        "seated_db_shoulder_press", "assisted_pull_up", "chest_supported_row",
        "leg_press", "hip_thrust", "bulgarian_split_squat",
    )
    private val barbellExercises = setOf("romanian_deadlift", "hip_thrust")

    private val turkishNames = mapOf(
        "machine_chest_press" to "Makinede göğüs itişi",
        "lat_pulldown" to "Üst makarada sırt çekişi",
        "incline_db_press" to "Eğimli sehpada dambılla göğüs itişi",
        "seated_cable_row" to "Oturarak kablolu sırt çekişi",
        "db_lateral_raise" to "Dambılla yana omuz açışı",
        "incline_db_curl" to "Eğimli sehpada dambılla kol bükme",
        "overhead_cable_triceps_ext" to "Baş üstü kablolu arka kol açışı",
        "hammer_curl" to "Çekiç tutuşla kol bükme",
        "hack_squat" to "Kızaklı çömelme",
        "romanian_deadlift" to "Rumen yerden çekişi",
        "leg_extension" to "Makinede diz açışı",
        "seated_leg_curl" to "Oturarak diz bükme",
        "hip_adduction" to "İç bacak kapama",
        "standing_calf_raise" to "Ayakta topuk kaldırma",
        "reverse_crunch" to "Ters mekik",
        "cable_crunch" to "Kablolu mekik",
        "incline_smith_press" to "Eğimli kızaklı barda göğüs itişi",
        "assisted_dip" to "Destekli paralel bar iniş-çıkışı",
        "high_to_low_cable_fly" to "Yukarıdan aşağı kablolu göğüs açışı",
        "low_to_high_cable_fly" to "Aşağıdan yukarı kablolu göğüs açışı",
        "seated_db_shoulder_press" to "Oturarak dambılla omuz itişi",
        "cable_lateral_raise" to "Kablolu yana omuz açışı",
        "rope_triceps_pushdown" to "Halatla arka kol itişi",
        "overhead_db_extension" to "Baş üstü dambılla arka kol açışı",
        "pallof_press" to "Kablolu dönmeye direnç itişi",
        "assisted_pull_up" to "Destekli barfiks",
        "chest_supported_row" to "Göğüs destekli sırt çekişi",
        "straight_arm_pulldown" to "Düz kolla üst makara çekişi",
        "face_pull" to "Yüze doğru halat çekişi",
        "reverse_pec_deck" to "Makinede ters omuz açışı",
        "db_shrug" to "Dambılla omuz silkme",
        "ez_preacher_curl" to "Dirsek destekli kıvrımlı barla kol bükme",
        "reverse_grip_ez_curl" to "Kıvrımlı barla ters tutuşta kol bükme",
        "wrist_curl_reverse" to "Bilek bükme ve ters bilek bükme",
        "leg_press" to "Makinede bacak itişi",
        "hip_thrust" to "Barla kalça itişi",
        "bulgarian_split_squat" to "Bulgar tek bacak çömelmesi",
        "lying_leg_curl" to "Yatarak diz bükme",
        "hip_abduction" to "Dış bacak açışı",
        "seated_calf_raise" to "Oturarak topuk kaldırma",
        "hanging_knee_raise" to "Asılı diz çekme",
        "side_plank" to "Yan karın duruşu",
        "ab_wheel" to "Karın tekerleği",
    )

    fun isCompound(id: String): Boolean = id in compoundExercises
    fun isBarbell(id: String): Boolean = id in barbellExercises
    fun turkishName(id: String): String? = turkishNames[id]

    private val groups = mapOf(
        "Göğüs" to "machine_chest_press,incline_db_press,incline_smith_press,assisted_dip,high_to_low_cable_fly,low_to_high_cable_fly",
        "Sırt" to "lat_pulldown,seated_cable_row,assisted_pull_up,chest_supported_row,straight_arm_pulldown,db_shrug",
        "Omuz" to "db_lateral_raise,seated_db_shoulder_press,cable_lateral_raise,face_pull,reverse_pec_deck",
        "Ön Kol" to "incline_db_curl,hammer_curl,ez_preacher_curl,reverse_grip_ez_curl,wrist_curl_reverse",
        "Arka Kol" to "overhead_cable_triceps_ext,rope_triceps_pushdown,overhead_db_extension",
        "Bacak" to "hack_squat,romanian_deadlift,leg_extension,seated_leg_curl,hip_adduction,standing_calf_raise,leg_press,hip_thrust,bulgarian_split_squat,lying_leg_curl,hip_abduction,seated_calf_raise",
        "Karın" to "reverse_crunch,cable_crunch,pallof_press,hanging_knee_raise,side_plank,ab_wheel",
    )

    fun muscleGroup(id: String): String =
        groups.entries.firstOrNull { id in it.value.split(",") }?.key ?: "Diğer"
}
