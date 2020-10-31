package io.nebula.platform.khala

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        KhalaRouter.init(
            KhalaRouterConfig.Builder(this.application)
                .build()
        )
        jump.setOnClickListener {
            KhalaRouter.instance()
                .route("/test/cpntActivity")
                .withString("paramString", "main string")
                .navigate()
        }
    }
}
