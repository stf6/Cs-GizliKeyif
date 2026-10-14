// ! Bu araç @stf6 / Cs-GizliKeyif için yazılmıştır.
package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class AShemaleTubePlugin : Plugin() {
    override fun load() {
        registerMainAPI(AShemaleTube())
    }
}
