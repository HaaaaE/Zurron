package compose.project.zurron

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform