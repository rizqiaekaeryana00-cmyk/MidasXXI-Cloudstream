cat > build.gradle.kts << 'EOF'
version = 1

cloudstream {
    language = "id"
    description = "MidasXXI - Indonesian Movies and TV Series"
    authors = listOf("fastabiqhidayatulah")
    status = 1
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://ubi.ac.id/wp-content/uploads/2024/01/midasxxi.png"
}
EOF
