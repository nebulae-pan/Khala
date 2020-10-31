package io.nebula.platform.cpnt.test

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import io.nebula.platform.khala.Khala
import io.nebula.platform.khala.KhalaRouter
import io.nebula.platform.khala.annotation.Inject
import io.nebula.platform.khala.annotation.Router
import kotlinx.android.synthetic.main.activity_main.*

@Router(path = "/test/cpntActivity")
class ComponentActivity : AppCompatActivity() {
    @Inject
    private var paramString: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        KhalaRouter.inject(this)
        text.append(" $paramString")
    }
}
