package compose.project.zurron

import io.exoquery.kmp.pprint

inline fun <reified T> T.pp() = println(pprint(this))
