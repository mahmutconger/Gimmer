package com.mcclabs.gimmer.data

object ExerciseMetadata {
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

    fun instructions(id: String): List<String> = when (id) {
        "machine_chest_press" -> listOf(
            "Koltuğu, tutacaklar göğsünün orta hizasına gelecek şekilde ayarla. Ayaklarını yere sağlam bas.",
            "Göğsünü yukarıda tut; kürek kemiklerini geriye ve aşağıya sabitle. Belini aşırı çukurlaştırma.",
            "Bileklerini düz tutarak tutacakları kontrollü biçimde ileri it. Dirseklerini sertçe kilitleme.",
            "Nefes alarak yavaşça başlangıç konumuna dön. Omuzlarını öne düşürme.",
        )
        "lat_pulldown" -> listOf(
            "Diz pedini sabitle ve barı omuz genişliğinden biraz geniş tut.",
            "Göğsünü yukarı kaldır; gövdeni hafifçe geriye eğ ve bu açıyı koru.",
            "Dirseklerini aşağıya yönlendirerek barı üst göğsüne yaklaştır. Barı ensene çekme.",
            "Gövdeni sallamadan barı yavaşça yukarı bırak. Omuz ekleminde ağrı varsa hareketi durdur.",
        )
        "hack_squat" -> listOf(
            "Sırtını ve omuzlarını mindere yasla. Ayaklarını omuz genişliğinde platforma yerleştir.",
            "Karnını sık, emniyet kilidini aç ve dizlerini ayak parmaklarınla aynı yöne yönlendir.",
            "Topuklarını kaldırmadan, belini minderden ayırmadan kontrollü biçimde alçal.",
            "Ayak tabanının tamamından kuvvet alarak yüksel. Dizlerini sertçe kilitleme ve emniyet kilidini kapat.",
        )
        else -> emptyList()
    }
}
