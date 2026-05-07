package com.greenbuddy.doh_approved_herb_identifier
import androidx.cardview.widget.CardView
import android.os.Looper
import com.google.mlkit.vision.objects.ObjectDetection
import java.util.concurrent.Executors
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Color
import android.graphics.Rect
import android.os.Environment
import android.widget.AdapterView
import android.os.Handler
import android.os.StatFs
import android.view.View
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.impl.Identifier
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.doh_approvedherbalplantidentifcation.HerbAdapter
import com.example.doh_approvedherbalplantidentifcation.HerbModel
import com.example.doh_approvedherbalplantidentifcation.SQLiteHelper
import com.google.mlkit.vision.common.InputImage
import java.io.ByteArrayOutputStream
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.greenbuddy.doh_approved_herb_identifier.NextActivity.Companion.REQUEST_CAMERA_CAPTURE
import com.greenbuddy.doh_approved_herb_identifier.ml.Greenbuddy
//import com.greenbuddy.doh_approvedherb_identifier.ml.GreenModel
//import com.greenbuddy.doh_approvedherb_identifier.ml.HerbalRecognationSemifi
//import com.greenbuddy.doh_approvedherb_identifier.ml.HerbalModel
//import com.greenbuddy.doh_approvedherb_identifier.ml.GreenbuddyModel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private lateinit var continuebtn: Button
    private var isReady = false // Moved here for cleaner scope

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. MUST BE FIRST: Initialize splash screen before everything else
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // 2. Set the condition to keep splash on screen
        splashScreen.setKeepOnScreenCondition { !isReady }

        // 3. Start your timer/loading task
        Handler(Looper.getMainLooper()).postDelayed({
            isReady = true
        }, 3000)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        continuebtn = findViewById(R.id.continuebtn)
        continuebtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#CC47734D"))
        continuebtn.setOnClickListener {
               val intent = Intent(this, NextActivity::class.java)
            startActivity(intent)
        }
    }
}

// BaseActivity.kt
abstract class BaseActivity : AppCompatActivity() {

    @get:LayoutRes
    protected abstract val layoutResourceId: Int

    // Changed to nullable or checked initialization to prevent "not initialized" crashes
    var btnhome: ImageButton? = null
    var about: ImageButton? = null
    var scnherbs: ImageButton? = null
    var upload: ImageButton? = null
    var settings: ImageButton? = null

    protected fun setupNavigation() {
        // Safe lookups: if the ID doesn't exist in the current XML, it won't crash
        btnhome = findViewById(R.id.home)
        about = findViewById(R.id.aboutus)
        scnherbs = findViewById(R.id.scnherbs)
        upload = findViewById(R.id.upload)
        settings = findViewById(R.id.setting)

        updateButtonSelection()

        // Only set listeners if the buttons actually exist in the layout
        btnhome?.setOnClickListener { navigateTo(NextActivity::class.java) }
        upload?.setOnClickListener { navigateTo(Classify::class.java)
            Toast.makeText(this, "Swipe Down!", Toast.LENGTH_SHORT).show()}
        scnherbs?.setOnClickListener {
            // FIX: If we are already in ScannerActivity, do nothing!
            if (this !is ScannerActivity) {
                navigateTo(ScannerActivity::class.java)
            }
        }
        about?.setOnClickListener {
            if (this !is AboutUs) {
                navigateTo(AboutUs::class.java)
            }
        }
        settings?.setOnClickListener {
            if (this !is Setting) {
                val intent = Intent(this, Setting::class.java)
                // Optional: Pass a signal to show the card
                intent.putExtra("SHOW_CARD", true)
                startActivity(intent)
            }
        }
    }

    private fun navigateTo(cls: Class<*>) {
        if (this::class.java == cls) return

        val intent = Intent(this, cls)
        // This flag clears the stack; use it carefully
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    private fun updateButtonSelection() {
        // Use ?.isSelected because these might be null in some layouts
        btnhome?.isSelected = (this is NextActivity)
        upload?.isSelected = (this is Classify)
        scnherbs?.isSelected = (this is ScannerActivity)
        about?.isSelected = (this is AboutUs)
        settings?.isSelected = (this is Setting)
    }
}
//second activity layout
class NextActivity : BaseActivity() { // CHANGED THIS

    override val layoutResourceId: Int = R.layout.second_activity



    companion object {
        const val REQUEST_CAMERA_CAPTURE = 101
    }
    //private val handler = Handler(Looper.getMainLooper())
    private lateinit var frontimg: ImageView

    private lateinit var frame: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.second_activity)

        // 1. Initialize the global navigation from the parent
        setupNavigation()

        // 2. Setup specific logic for THIS activity
        frontimg = findViewById(R.id.front)
        frame = findViewById(R.id.fl)

        //startSingleImageFlip()

        val sav = findViewById<TextView>(R.id.saved)
        sav.setOnClickListener {
            val intent = Intent(this, HerbActivity::class.java)
            startActivity(intent)
        }
        //image card for browsing


        // Akapulko
        val akacard = findViewById<CardView>(R.id.akapulkocard)
        akacard.setOnClickListener {
            val intent = Intent(this, Category::class.java)
            intent.putExtra("HERB_NAME", "Akapulko")
            intent.putExtra("HERB_IMAGE_ID", R.drawable.aa)
            Toast.makeText(this, "Scroll Down!", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }

        // Ampalaya
        val ampacard = findViewById<CardView>(R.id.ampalayacard)
        ampacard.setOnClickListener {
            val intent = Intent(this, Category::class.java)
            intent.putExtra("HERB_NAME", "Ampalaya")
            intent.putExtra("HERB_IMAGE_ID", R.drawable.am)
            Toast.makeText(this, "Scroll Down!", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }

        // Bawang
        val bacard = findViewById<CardView>(R.id.bawangcard)
        bacard.setOnClickListener {
            val intent = Intent(this, Category::class.java)
            intent.putExtra("HERB_NAME", "Bawang")
            intent.putExtra("HERB_IMAGE_ID", R.drawable.bawang)
            Toast.makeText(this, "Scroll Down!", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }

        // Bayabas
        val bayacard = findViewById<CardView>(R.id.bayabascard)
        bayacard.setOnClickListener {
            val intent = Intent(this, Category::class.java)
            intent.putExtra("HERB_NAME", "Bayabas")
            intent.putExtra("HERB_IMAGE_ID", R.drawable.bayabas)
            Toast.makeText(this, "Scroll Down!", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }

        // Lagundi
        val lagucard = findViewById<CardView>(R.id.lagundicard)
        lagucard.setOnClickListener {
            val intent = Intent(this, Category::class.java)
            intent.putExtra("HERB_NAME", "Lagundi")
            intent.putExtra("HERB_IMAGE_ID", R.drawable.lagundi)
            Toast.makeText(this, "Scroll Down!", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }

        // Niyog-Niyogan
        val niyogcard = findViewById<CardView>(R.id.niyog_niyogancard)
        niyogcard.setOnClickListener {
            val intent = Intent(this, Category::class.java)
            intent.putExtra("HERB_NAME", "Niyog-Niyogan")
            intent.putExtra("HERB_IMAGE_ID", R.drawable.nnnn)
            Toast.makeText(this, "Scroll Down!", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }

        // Pansit-Pansitan
        val pancard = findViewById<CardView>(R.id.pansit_pansitancard)
        pancard.setOnClickListener {
            val intent = Intent(this, Category::class.java)
            intent.putExtra("HERB_NAME", "Pancit-Pancitan")
            intent.putExtra("HERB_IMAGE_ID", R.drawable.pp)
            Toast.makeText(this, "Scroll Down!", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }

        // Sambong
        val samcard = findViewById<CardView>(R.id.sambongcard)
        samcard.setOnClickListener {
            val intent = Intent(this, Category::class.java)
            intent.putExtra("HERB_NAME", "Sambong")
            intent.putExtra("HERB_IMAGE_ID", R.drawable.sambong)
            Toast.makeText(this, "Scroll Down!", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }

        // Tsaang-Gubat
        val tsacard = findViewById<CardView>(R.id.tsang_gubatcard)
        tsacard.setOnClickListener {
            val intent = Intent(this, Category::class.java)
            intent.putExtra("HERB_NAME", "Tsaang-Gubat")
            intent.putExtra("HERB_IMAGE_ID", R.drawable.tsts)
            Toast.makeText(this, "Scroll Down!", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }

        // Yerba Buena
        val yeracard = findViewById<CardView>(R.id.ybcard)
        yeracard.setOnClickListener {
            val intent = Intent(this, Category::class.java)
            intent.putExtra("HERB_NAME", "Yerba Buena")
            intent.putExtra("HERB_IMAGE_ID", R.drawable.yb)
            Toast.makeText(this, "Scroll Down!", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        }






    }
    override fun onResume() {
        super.onResume()
    }

// Update your card listeners


    /*private fun startSingleImageFlip() {
        val flipInterval = 3000L
        handler.post(object : Runnable {
            override fun run() {
                val flip = AnimatorInflater.loadAnimator(this@NextActivity, R.animator.front_animator)
                flip.setTarget(frontimg)
                flip.start()
                handler.postDelayed(this, flipInterval)
            }
        })*/
}

// Third activity for browsing
class Category : BaseActivity() {

    override val layoutResourceId: Int = R.layout.category

    private lateinit var itemImage: ImageView
    private lateinit var herbalcategory: TextView
    private lateinit var itemDiseases: TextView // New field
    private lateinit var itemDefinition: TextView
    private lateinit var itemPreparation: TextView
    private lateinit var itemWarning: TextView
    private lateinit var itemLocation: TextView
    private lateinit var defsv: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.category)

        setupNavigation()

        // 1. Initialize Views
        itemImage = findViewById(R.id.item_image)
        herbalcategory = findViewById(R.id.herbalcategory)
        itemDiseases = findViewById(R.id.item_diseases)    // Set this up in your XML
        itemDefinition = findViewById(R.id.item_definition)
        itemPreparation = findViewById(R.id.item_preparation)
        itemWarning = findViewById(R.id.item_warning)
        itemLocation = findViewById(R.id.item_location)
        defsv = findViewById(R.id.svdef)

        // 2. Get Data from Intent
        val herbName = intent.getStringExtra("HERB_NAME")
        val herbImage = intent.getIntExtra("HERB_IMAGE_ID", 0)

        // 3. Display Data
        if (herbName != null) {
            herbalcategory.text = herbName
            itemImage.setImageResource(herbImage)
            loadHerbDetails(herbName)
        }
    }

    private fun loadHerbDetails(name: String) {
        when (name) {
            "Akapulko" -> {
                itemDiseases.text = "Treats: Ringworm, Scabies, Eczema, Tinea Flava."
                itemDefinition.text = "A tropical shrub known for its antifungal properties."
                itemPreparation.text = "Pound fresh leaves and apply the juice to the skin twice daily."
                itemWarning.text = "For external use only. Avoid contact with eyes."
                itemLocation.text = "Found in open fields and waste places throughout the Philippines."
            }
            "Ampalaya" -> {
                itemDiseases.text = "Treats: Diabetes (Mild Non-insulin dependent), Hemorrhoids."
                itemDefinition.text = "A climbing vine with bitter-tasting fruit used for blood sugar management."
                itemPreparation.text = "Boil chopped leaves (1 cup leaves to 2 cups water) for 15 minutes. Drink 1/3 cup thrice a day."
                itemWarning.text = "Excessive intake may cause stomach cramps."
                itemLocation.text = "Widely cultivated in the Philippines for its edible fruit."
            }
            "Bawang" -> {
                itemDiseases.text = "Treats: Hypertension, High Cholesterol, Toothache."
                itemDefinition.text = "A common kitchen spice with strong medicinal properties for the heart."
                itemPreparation.text = "For hypertension: Eat 2 cloves of roasted/blanched garlic after meals. For toothache: Pound a clove and apply to the cavity."
                itemWarning.text = "Avoid eating on an empty stomach to prevent gastric irritation."
                itemLocation.text = "Cultivated in various provinces, especially in Ilocos."
            }
            "Bayabas" -> {
                itemDiseases.text = "Treats: Diarrhea, Wounds, Gum Infection (Gingivitis)."
                itemDefinition.text = "A tree whose leaves act as a powerful natural antiseptic."
                itemPreparation.text = "Boil leaves to use as a wash for wounds or as a gargle for swollen gums."
                itemWarning.text = "Do not use for prolonged periods as a mouthwash (may stain teeth)."
                itemLocation.text = "Common throughout the Philippines in backyards and forests."
            }
            "Lagundi" -> {
                itemDiseases.text = "Treats: Cough, Asthma, Fever, Rheumatism."
                itemDefinition.text = "A large shrub officially endorsed by the DOH for respiratory relief."
                itemPreparation.text = "Boil 1 cup of chopped leaves in 2 cups of water until the water is reduced to half."
                itemWarning.text = "Consult a doctor if symptoms persist beyond 3 days."
                itemLocation.text = "Grows wild in waste places and is often planted in gardens."
            }
            "Niyog-Niyogan" -> {
                itemDiseases.text = "Treats: Intestinal Worms (Ascariasis)."
                itemDefinition.text = "A climbing vine known as the 'Chinese Honeysuckle'."
                itemPreparation.text = "Eat the seeds (dried) two hours after dinner. (Adults: 8-10 seeds; Children 4-7: 4-5 seeds)."
                itemWarning.text = "Hiccups may occur if too many seeds are eaten."
                itemLocation.text = "Grows wild in the Philippines and is often used as an ornamental plant."
            }
            "Pancit-Pancitan" -> {
                itemDiseases.text = "Treats: Gout, Arthritis, Skin Inflammation."
                itemDefinition.text = "A heart-shaped, fleshy-leafed herb that lowers uric acid levels."
                itemPreparation.text = "Can be eaten as a fresh salad or boiled as tea."
                itemWarning.text = "Ensure the plant is washed thoroughly if eaten raw."
                itemLocation.text = "Commonly grows in damp, shaded areas and stone walls."
            }
            "Sambong" -> {
                itemDiseases.text = "Treats: Kidney Stones (Edema), Hypertension."
                itemDefinition.text = "A tall herb that acts as a powerful diuretic."
                itemPreparation.text = "Boil leaves and drink as tea to help flush out small kidney stones."
                itemWarning.text = "Not for patients with kidney failure or heart disease-related edema."
                itemLocation.text = "Abundant in open fields and mountain slopes."
            }
            "Tsaang-Gubat" -> {
                itemDiseases.text = "Treats: Stomach Ache, Diarrhea, Abdominal Pain."
                itemDefinition.text = "A small tree used traditionally as a general health tonic and stomach medicine."
                itemPreparation.text = "Boil leaves in water for 15 minutes. Drink while warm."
                itemWarning.text = "Safe for general use, but seek medical help for severe dehydration."
                itemLocation.text = "Found in dry thickets and secondary forests."
            }
            "Yerba Buena" -> {
                itemDiseases.text = "Treats: Body Aches, Headache, Toothache, Cough."
                itemDefinition.text = "A creeping mint plant used as a natural analgesic (painkiller)."
                itemPreparation.text = "Crush leaves and apply to the forehead for headaches, or boil as tea for body pain."
                itemWarning.text = "Avoid using the concentrated oil directly on small children."
                itemLocation.text = "Thrives in high altitudes but can be grown in pots in lowlands."
            }
            else -> {
                itemDiseases.text = "Specific usage data not found."
                itemDefinition.text = "Details currently unavailable."
            }
        }
    }
}   

//Fourth Activity Ddi makita an saved predicted or classified photos
class HerbActivity : BaseActivity() {

    override val layoutResourceId: Int = R.layout.herblistview

    private lateinit var herblist: ListView
    private lateinit var sqLiteHelper: SQLiteHelper
    private var herbmod = mutableListOf<HerbModel>()

    private lateinit var adapter: HerbAdapter

    // Pagination variables
    private var currentOffset = 0
    private val pageSize = 15 // Increased for better initial fill
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.herblistview)

        setupNavigation()



        herblist = findViewById(R.id.herbListView)
        sqLiteHelper = SQLiteHelper(this)

        adapter = HerbAdapter(this, herbmod)
        herblist.adapter = adapter

        // 1. Initial Load
        loadNextBatch()


        // 2. Set Scroll Listener for "Infinite Scroll"
        herblist.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}

            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                // If we are at the bottom and not currently loading, fetch more
                if (!isLoading && (firstVisibleItem + visibleItemCount >= totalItemCount) && totalItemCount > 0) {
                    loadNextBatch()
                }
            }
        })
    }

    private fun loadNextBatch() {
        isLoading = true
        try {
            val herbs = sqLiteHelper.getHerbs(pageSize, currentOffset)

            if (herbs.isNotEmpty()) {
                herbmod.addAll(herbs)
                adapter.notifyDataSetChanged()
                currentOffset += herbs.size
            }
        } catch (ex: Exception) {
            Log.e("HerbActivity", "Failed to load herbs", ex)
        } finally {
            isLoading = false
        }
    }

    override fun onResume() {
        super.onResume()
    }

}

//Fifth Activity Ini na part an pag classify ha image or pag predict
class Classify : BaseActivity() {

    override val layoutResourceId: Int = R.layout.captured_analysis

    companion object {
        const val SELECT_IMAGE = 101
    }

    private lateinit var classification: Button
    private lateinit var Imgcapture: ImageView
    private lateinit var Imglabel: TextView
    private lateinit var Up: Button
    private lateinit var Donebtn: Button
    private lateinit var Save: Button
    private lateinit var Desc: TextView
    private lateinit var Safe: TextView
    private lateinit var labelsafe: TextView
    private lateinit var labeldesc: TextView
    private val Request_Camera_Capture = REQUEST_CAMERA_CAPTURE
    private lateinit var sqLiteHelper: SQLiteHelper
    private lateinit var preplbl: TextView
    private lateinit var prepar: TextView
    private lateinit var name: TextView
    private var safety_warning: String = ""
    private var description: String = ""
    private var preparation: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.captured_analysis)

        setupNavigation()

        Imgcapture = findViewById(R.id.imagecapture)
        Imglabel = findViewById(R.id.label)
        Save = findViewById(R.id.savebutton)
        Up = findViewById(R.id.uploadbttn)
        Up.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#A7F3D0"))
        classification = findViewById(R.id.classify)
        classification.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#A7F3D0"))
        Donebtn = findViewById(R.id.donebtn)
        Desc = findViewById(R.id.descriptionlabel)
        labelsafe = findViewById(R.id.safe)
        labeldesc = findViewById(R.id.labeldes)
        Safe = findViewById(R.id.safety)
        preplbl = findViewById(R.id.preplabel)
        prepar = findViewById(R.id.prep)
        name = findViewById(R.id.name)

        val intent = intent




        val byteArray = intent.getByteArrayExtra("capturedImage")
        if (byteArray != null) {
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            Imgcapture.setImageBitmap(bitmap) // Set the bitmap to the ImageView
            Imgcapture.visibility = View.VISIBLE
            Log.d("ImageStatus", "Captured image displayed")
        }

        sqLiteHelper = SQLiteHelper(this)


        //  Select image from gallery
        Up.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            startActivityForResult(intent, SELECT_IMAGE)
            Imglabel.setText(" ")
            labeldesc.setText(" ")
            Safe.setText(" ")
            preplbl.setText("")
        }

        //  Predict button
        classification.setOnClickListener {
            val drawable = Imgcapture.drawable
            if (drawable != null) {
                val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                    drawable.bitmap
                } else {
                    // Handle other drawable types if necessary, e.g., create a new bitmap
                    val bmp = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    drawable.draw(canvas)
                    bmp
                }
                runPrediction(bitmap)
            } else { Toast.makeText(this, "No image to predict", Toast.LENGTH_SHORT).show()}
        }

        Donebtn.setOnClickListener {
            val intent = Intent(this, HerbActivity::class.java) // Replace MainActivity with your home class name
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        Save.setOnClickListener {
            saveCurrentResult()
        }

        // Inside Classify.kt onCreate

        val plantNameExtra = intent.getStringExtra("PLANT_NAME")
        if (byteArray != null) {
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            Imgcapture.setImageBitmap(bitmap)
            Imgcapture.visibility = View.VISIBLE

            // If we passed a name from the scanner, display it and run prediction automatically
            if (plantNameExtra != null) {
                Imglabel.text = plantNameExtra
                // Since we already have the bitmap, we can run prediction to fill
                // Description, Safety, and Preparation immediately
                runPrediction(bitmap)
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == SELECT_IMAGE && data != null) {
            val imageUri = data.data
            val imageBitmap = imageUri?.let {
                MediaStore.Images.Media.getBitmap(contentResolver, it)
            }

            if (imageBitmap != null) {
                Imgcapture.setImageBitmap(imageBitmap) // Set the bitmap to the ImageView
                Imgcapture.visibility = View.VISIBLE
                Log.d("ImageStatus", "Image selected and displayed")
            } else {
                Toast.makeText(this, "Failed to retrieve image", Toast.LENGTH_SHORT).show()
            }
        }
        if (resultCode == RESULT_OK && requestCode == Request_Camera_Capture && data != null) {
            val photo = data.extras?.get("data") as? Bitmap
            if (photo != null) {
                Imgcapture.setImageBitmap(photo) // Set the bitmap to the ImageView
                Imgcapture.visibility = View.VISIBLE
                Log.d("ImageStatus", "New captured image displayed for re-scan")
                Imglabel.text = "" // Clear previous label
            }
        }
    }

    //  Run Classification tima pag deploy san model
    private fun runPrediction(bitmap: Bitmap) {
        // 1. DECLARE THE CORRECT SIZE HERE
        val modelInputSize = 224

        val dimension = Math.min(bitmap.width, bitmap.height)
        val centerCroppedBitmap = Bitmap.createBitmap(bitmap, (bitmap.width - dimension) / 2, (bitmap.height - dimension) / 2, dimension, dimension)
        val scaledBitmap = Bitmap.createScaledBitmap(centerCroppedBitmap, modelInputSize, modelInputSize, true)
        Imgcapture.setImageBitmap(scaledBitmap)


        // Load labels from assets
        val labelList = assets.open("label.txt")
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val model = Greenbuddy.newInstance(this)

        // 2. Preprocess image - CHANGE 180 TO modelInputSize (160)
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(modelInputSize, modelInputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 1f))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        // 3. Create TensorBuffer - CHANGE 180 TO modelInputSize (160)
        val inputFeature0 = TensorBuffer.createFixedSize(
            intArrayOf(1, modelInputSize, modelInputSize, 3), DataType.FLOAT32
        )
        inputFeature0.loadBuffer(tensorImage.buffer)

        // Run model
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer.floatArray

        // Apply scores (probabilities)
        val probabilities = outputFeature0

        // Log all class scores for debugging
        probabilities.forEachIndexed { idx, prob ->
            val name = labelList.getOrNull(idx) ?: "Class $idx"
            Log.d("MODEL_OUTPUT", "$name: ${"%.4f".format(prob)}")
        }

        // Find top prediction
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] }
        val maxProb = maxIndex?.let { probabilities[it] } ?: 0f
        val confidenceThreshold = 0.3f

        val predictedLabel = if (maxProb >= confidenceThreshold &&
            maxIndex != null &&
            maxIndex in labelList.indices) {
            labelList[maxIndex]
        } else {
            "Invalid"
        }

        // Preparation, Description, and Safety Warning Logic
        preparation = when (predictedLabel) {
            "Akapulko" -> "📝 Boil leaves and apply the decoction on affected skin areas twice daily for fungal infections."
            "Ampalaya" -> "📝 Boil the leaves or fruit and drink the decoction twice a day to help manage blood sugar."
            "Bawang" -> "📝 Crush cloves and eat raw or mix with food to help lower cholesterol and blood pressure."
            "Bayabas" -> "📝 Boil leaves for 15 minutes and use as a wash for wounds or as mouth rinse for gum infections."
            "Lagundi" -> "📝 Boil leaves and drink the decoction three times a day to relieve cough and fever."
            "Niyog_Niyogan" -> "📝 Roast and eat mature seeds (2 for children, 4 for adults) after meals as a dewormer."
            "Pansit_Pansitan" -> "📝 Eat fresh leaves as salad or boil and drink the decoction twice daily to relieve gout and arthritis."
            "Sambong" -> "📝 Boil leaves and drink the decoction two times a day for kidney stones or urinary issues."
            "Tsaang_Gubat" -> "📝 Boil leaves for 15 minutes, cool, and drink as tea to ease stomach pain or diarrhea."
            "Yerba_Buena" -> "📝 Boil leaves and drink as tea for headaches or stomach pain, or use as a mouthwash."
            else -> "📝 No preparation details available."
        }

        description = when (predictedLabel) {
            "Akapulko" -> "📖 Known for its antifungal properties — used to treat ringworm, scabies, and eczema."
            "Ampalaya" -> "📖 Helps manage diabetes and lower blood sugar levels."
            "Bawang" -> "📖 Known for reducing cholesterol and helping control blood pressure."
            "Bayabas" -> "📖 Acts as an antiseptic — used to clean wounds and as a mouthwash for tooth and gum infections."
            "Lagundi" -> "📖 Used to treat cough, asthma, and fever."
            "Niyog_Niyogan" -> "📖 Used as a dewormer and for treating headaches and skin ulcers."
            "Pansit_Pansitan" -> "📖 Helps with gout, arthritis, and skin disorders — lowers uric acid levels."
            "Sambong" -> "📖 A diuretic herb used for kidney stones, fever, and respiratory infections."
            "Tsaang_Gubat" -> "📖 Used for stomach pain, diarrhea, and as a mouthwash."
            "Yerba_Buena" -> "📖 Known for pain relief, digestive support, and oral care."
            else -> "📖 No description available."
        }

        safety_warning = when (predictedLabel) {
            "Akapulko" -> "⚠️ For external use only. Avoid contact with eyes and open wounds. Prolonged use may cause skin irritation."
            "Ampalaya" -> "⚠️ Not recommended for pregnant women or people taking diabetes medication without medical advice."
            "Bawang" -> "⚠️ May cause stomach irritation if taken in excess. Avoid before surgery due to blood-thinning effects."
            "Bayabas" -> "⚠️ Use guava leaf decoction in moderation; excessive use may lead to constipation or stomach upset."
            "Lagundi" -> "⚠️ Not advisable for pregnant or breastfeeding women. May cause dizziness in sensitive individuals."
            "Niyog_Niyogan" -> "⚠️ Seeds are toxic in large amounts. Always use in recommended doses only."
            "Pansit_Pansitan" -> "⚠️ May cause mild stomach discomfort. Avoid if pregnant or breastfeeding."
            "Sambong" -> "⚠️ Avoid long-term use without medical advice. Not suitable for people with kidney disease."
            "Tsaang_Gubat" -> "⚠️ Avoid excessive consumption as it may cause constipation. Not for long-term use."
            "Yerba_Buena" -> "⚠️ Avoid excessive intake; may cause drowsiness or stomach irritation."
            else -> "⚠️ No safety warning available. Consult a healthcare professional before use."
        }

        // UI Updates
        if (predictedLabel == "Invalid") {
            Imglabel.setTextColor(Color.RED)
            Imglabel.text = predictedLabel
            Desc.visibility = View.GONE
            Safe.visibility = View.GONE
            labeldesc.visibility = View.GONE
            labelsafe.visibility = View.GONE
            preplbl.visibility = View.GONE
            prepar.visibility = View.GONE
            name.visibility = View.GONE
        } else {
            Imglabel.setTextColor(Color.BLACK)
            val confidenceText = "%.2f".format(maxProb * 100)
            Imglabel.text = "$predictedLabel (Confidence: $confidenceText%)"
            Desc.visibility = View.VISIBLE
            Safe.visibility = View.VISIBLE
            labeldesc.visibility = View.VISIBLE
            labelsafe.visibility = View.VISIBLE
            prepar.visibility = View.VISIBLE
            preplbl.visibility = View.VISIBLE
            name.visibility = View.VISIBLE
            labeldesc.text = description
            Safe.text = safety_warning
            preplbl.text = preparation // Corrected variable binding
        }

        model.close()
    }

    private fun saveCurrentResult() {
        val sqLiteHelper = SQLiteHelper(this)
        val drawable = Imgcapture.drawable
        val labelText = Imglabel.text.toString()

        if (drawable == null || labelText.isEmpty() || labelText == "Invalid" || labelText == "Invalid!") {
            Toast.makeText(this, "No valid classification result to save!", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Get the full bitmap from the drawable
        val fullBitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
            drawable.bitmap
        } else {
            val bmp = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            drawable.draw(canvas)
            bmp
        }

        // 2. RESIZE THE BITMAP (Critical Fix)
        // Resize to a smaller max dimension (e.g., 300px or 500px) to keep file size small
        val maxDimension = 400
        val ratio = Math.min(
            maxDimension.toDouble() / fullBitmap.width,
            maxDimension.toDouble() / fullBitmap.height
        )
        val width = (ratio * fullBitmap.width).toInt()
        val height = (ratio * fullBitmap.height).toInt()
        val resizedBitmap = Bitmap.createScaledBitmap(fullBitmap, width, height, true)

        // 3. Compress to JPEG to further reduce size
        val stream = ByteArrayOutputStream()
        // Quality 80 is a good balance.
        // IMPORTANT: Use JPEG instead of PNG for photos (much smaller size)
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val imageBytes = stream.toByteArray()

        if (imageBytes.isEmpty()) {
            Log.e("SaveResult", "Image byte array is empty")
            Toast.makeText(this, "Image conversion failed!", Toast.LENGTH_SHORT).show()
            return
        }

        // Check size immediately
        if (imageBytes.size > 1000000) { // If larger than 1MB
            Toast.makeText(this, "Image is too large to save!", Toast.LENGTH_SHORT).show()
            return
        }

        // Parse label and confidence
        val parts = labelText.split("(")
        val herbalname = parts[0].trim()
        val confidenceString = if (parts.size > 1) parts[1].replace(")", "").trim() else ""

        val herballevel = try {
            // Handle cases like "Confidence: 95.50%"
            val cleanString = confidenceString.replace("Confidence:", "").replace("%", "").trim()
            cleanString.toFloat() / 100.0f
        } catch (e: Exception) {
            Log.e("SaveResult", "Could not parse confidence: $confidenceString", e)
            0.0f
        }

        // Use the class-level variables you already populated in runPrediction
        val herbaldescription = this.description
        val herbalsafetywarn = this.safety_warning

        // Attempt to insert
        val success = sqLiteHelper.insertHerb(
            herbalname,
            herballevel,
            herbaldescription,
            herbalsafetywarn,
            imageBytes
        )

        if (success) {
            Toast.makeText(this, "Saved to History!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Save failed!", Toast.LENGTH_SHORT).show()
        }
    }
}

class ScannerActivity : BaseActivity() {

    private lateinit var Scan: ImageButton
    private lateinit var Prev: PreviewView
    private lateinit var Txtres: TextView
    private lateinit var view: View
    private lateinit var bottomCard: androidx.cardview.widget.CardView
    private lateinit var plantName: TextView
    private lateinit var plantImage: ImageView
    private lateinit var herbalModel: Greenbuddy
    private lateinit var objectDetector: com.google.mlkit.vision.objects.ObjectDetector
    private var herbalLabels: List<String> = emptyList()

    // Optimization: Background thread and pre-allocated buffers
    private val modelExecutor = Executors.newSingleThreadExecutor()
    private var isModelProcessing = false
    private lateinit var inputFeature0: TensorBuffer
    private lateinit var byteBuffer: ByteBuffer
    private lateinit var intValues: IntArray

    private var isProcessingPaused = false

    override val layoutResourceId: Int = R.layout.viewfinder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.viewfinder)

        // Pre-allocate model buffers once to save CPU and memory
        val modelInputSize = 224
        inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, modelInputSize, modelInputSize, 3), DataType.FLOAT32)
        byteBuffer = ByteBuffer.allocateDirect(4 * modelInputSize * modelInputSize * 3).order(ByteOrder.nativeOrder())
        intValues = IntArray(modelInputSize * modelInputSize)

        setupNavigation()
        setupHerbalModel()
        loadLabels()

        bottomCard = findViewById(R.id.bottomCard)
        bottomCard.visibility = View.GONE
        plantName = findViewById(R.id.cardlabel)
        plantImage = findViewById(R.id.cardIm)
        Scan = findViewById<ImageButton>(R.id.Scan)
        Prev = findViewById<PreviewView>(R.id.Prev)
        Txtres = findViewById<TextView>(R.id.Txtres)
        view = findViewById<View>(R.id.targetBox)

        Scan.setOnClickListener { checkCameraPermission() }

        bottomCard.setOnClickListener {
            val drawable = plantImage.drawable as? android.graphics.drawable.BitmapDrawable
            val bitmap = drawable?.bitmap

            if (bitmap != null) {
                val intent = Intent(this, Classify::class.java)
                intent.putExtra("PLANT_NAME", plantName.text.toString())

                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                val byteArray = stream.toByteArray()
                intent.putExtra("capturedImage", byteArray)

                startActivity(intent)
            } else {
                Toast.makeText(this, "No image captured yet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupHerbalModel() {
        try {
            herbalModel = Greenbuddy.newInstance(this)
            val options = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .build()
            objectDetector = ObjectDetection.getClient(options)
        } catch (e: Exception) {
            Log.e("Scanner", "Initialization failed: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    private fun startCamera() {
        Scan.visibility = View.GONE
        Prev.visibility = View.VISIBLE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(Prev.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                // Use background executor to prevent ANRs
                imageAnalysis.setAnalyzer(modelExecutor) { imageProxy ->
                    if (isProcessingPaused || isModelProcessing) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    processImageProxy(imageProxy)
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
            } catch (exc: Exception) {
                Log.e("Scanner", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (!::objectDetector.isInitialized) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        objectDetector.process(image)
            .addOnSuccessListener { objects ->
                if (isProcessingPaused) return@addOnSuccessListener

                updateTargetBoxToCenter()

                if (objects.isEmpty()) {
                    runOnUiThread {
                        Txtres.text = "Align plant in box..."
                        Txtres.setTextColor(Color.WHITE)
                        view.alpha = 0.5f
                    }
                    return@addOnSuccessListener
                }

                for (detectedObject in objects) {
                    if (isProcessingPaused || isModelProcessing) break

                    val boundingBox = detectedObject.boundingBox
                    val fullBitmap = Prev.bitmap

                    if (fullBitmap != null) {
                        isModelProcessing = true // Lock the model

                        // Map coordinates from ML Kit box to Bitmap
                        val isRotated = rotation == 90 || rotation == 270
                        val frameWidth = if (isRotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
                        val frameHeight = if (isRotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()

                        val scaleX = fullBitmap.width / frameWidth
                        val scaleY = fullBitmap.height / frameHeight

                        val scaledRect = Rect(
                            (boundingBox.left * scaleX).toInt(),
                            (boundingBox.top * scaleY).toInt(),
                            (boundingBox.right * scaleX).toInt(),
                            (boundingBox.bottom * scaleY).toInt()
                        )

                        val croppedBitmap = cropBitmap(fullBitmap, scaledRect)
                        runHerbalModel(croppedBitmap)
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun runHerbalModel(bitmap: Bitmap) {
        val modelInputSize = 224

        // Square crop to match model expectations
        val dimension = Math.min(bitmap.width, bitmap.height)
        val centerCroppedBitmap = Bitmap.createBitmap(bitmap, (bitmap.width - dimension) / 2, (bitmap.height - dimension) / 2, dimension, dimension)
        val scaledBitmap = Bitmap.createScaledBitmap(centerCroppedBitmap, modelInputSize, modelInputSize, true)

        // Clear and fill the reusable buffer
        byteBuffer.clear()
        scaledBitmap.getPixels(intValues, 0, modelInputSize, 0, 0, modelInputSize, modelInputSize)

        for (pixel in intValues) {
            byteBuffer.putFloat(((pixel shr 16 and 0xFF) / 127.5f) - 1.0f)
            byteBuffer.putFloat(((pixel shr 8 and 0xFF) / 127.5f) - 1.0f)
            byteBuffer.putFloat(((pixel and 0xFF) / 127.5f) - 1.0f)
        }

        inputFeature0.loadBuffer(byteBuffer)
        val outputs = herbalModel.process(inputFeature0)
        val scores = outputs.outputFeature0AsTensorBuffer.floatArray
        val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: -1

        runOnUiThread {
            isModelProcessing = false // Unlock
            if (maxIndex != -1 && maxIndex < herbalLabels.size) {
                val confidence = scores[maxIndex]
                val detectedLabel = herbalLabels[maxIndex]

                if (confidence > 0.65f && !detectedLabel.contains("Invalid!", ignoreCase = true)) {
                    isProcessingPaused = true

                    try {
                        ProcessCameraProvider.getInstance(this).get().unbindAll()
                    } catch (e: Exception) {
                        Log.e("Scanner", "Unbind failed: ${e.message}")
                    }

                    Txtres.text = "$detectedLabel (${(confidence * 100).toInt()}%)"
                    Txtres.setTextColor(Color.GREEN)
                    bottomCard.visibility = View.VISIBLE
                    plantImage.setImageBitmap(bitmap)
                    plantName.text = detectedLabel

                    Handler(Looper.getMainLooper()).postDelayed({
                        isProcessingPaused = false
                        bottomCard.visibility = View.GONE
                        Txtres.text = "Scanning..."
                        Txtres.setTextColor(Color.WHITE)
                        startCamera()
                    }, 3000)
                }
            }
        }
    }

    private fun updateTargetBoxToCenter() {
        runOnUiThread {
            val size = (Prev.width * 0.75).toInt()
            if (view.layoutParams.width != size) {
                val params = view.layoutParams
                params.width = size
                params.height = size
                view.layoutParams = params
                view.setBackgroundResource(R.drawable.scanner_design)
            }
            view.x = Prev.left + (Prev.width / 2f) - (size / 2f)
            view.y = Prev.top + (Prev.height / 2f) - (size / 2f)
            view.visibility = View.VISIBLE
        }
    }

    fun cropBitmap(source: Bitmap, rect: Rect): Bitmap {
        val left = rect.left.coerceIn(0, source.width - 1)
        val top = rect.top.coerceIn(0, source.height - 1)
        val width = rect.width().coerceAtMost(source.width - left)
        val height = rect.height().coerceAtMost(source.height - top)
        return if (width > 10 && height > 10) {
            Bitmap.createBitmap(source, left, top, width, height)
        } else source
    }

    private fun loadLabels() {
        try {
            val inputStream = assets.open("label.txt")
            herbalLabels = inputStream.bufferedReader().readLines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("Scanner", "Error loading labels: ${e.message}")
        }
    }
}

class AboutUs : BaseActivity() {

    // Add this line to satisfy the abstract requirement
    override val layoutResourceId: Int = R.layout.aboutus // Ensure 'about_us' matches your XML filename

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aboutus)

        setupNavigation()
    }
}

class Setting : BaseActivity(){
    override val layoutResourceId: Int = R.layout.universal_btn

    private lateinit var dbHelper: SQLiteHelper

    override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
        setContentView(R.layout.universal_btn)

        dbHelper = SQLiteHelper(this)

        val card = findViewById<CardView>(R.id.contentCard1)
        val cardm = findViewById<CardView>(R.id.mainCards)
        val shouldShow = intent.getBooleanExtra("SHOW_CARD", false)
        if (shouldShow) {
            cardm.visibility = View.VISIBLE
        } else {
            card.visibility = View.GONE
        }

        val btnClear = findViewById<Button>(R.id.btnClearData)

        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete All Data & Cache?")
                .setMessage("This will erase all saved plants and clear temporary files. This action cannot be undone.")
                .setPositiveButton("Clear All") { _, _ ->
                    // 1. Clear Database Data
                    val isCleared = dbHelper.clearAllHerbs()

                    // 2. Clear App Cache Files
                    val cacheCleared = clearAppCache()

                    if (isCleared || cacheCleared) {
                        Toast.makeText(this, "Data and Cache cleared", Toast.LENGTH_SHORT).show()
                        // Refresh UI to show 0.00 MB
                        updateStorageUI()
                        updateStorageDetails()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }


        setupNavigation()
        updateStorageUI()
        updateStorageDetails()


    }
    override fun onResume() {
        super.onResume()
        // Refresh every time the user sees this screen
        updateStorageUI()
        updateStorageDetails()
    }

    private fun clearAppCache(): Boolean {
        return try {
            val dir = cacheDir
            dir.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun updateStorageUI() {
        val count = dbHelper.getSavedPlantsCount() // Now returns unique count
        val maxPlants = 10
        val percentage = (count.toFloat() / maxPlants * 100).toInt()

        val progressBar = findViewById<ProgressBar>(R.id.plantProgressBar)
        val tvPercentage = findViewById<TextView>(R.id.tvPercentage)
        val tvSavedCount = findViewById<TextView>(R.id.tvSavedCount)

        progressBar?.progress = count
        tvPercentage?.text = "$percentage%"

        // Updated text to reflect "Unique Species"
        tvSavedCount?.text = "$count of $maxPlants species identified"
    }
    private fun updateStorageDetails() {
        //Model Storage consumed
        val modelFileName = "Herbal_Recognation_Semifi.tflite"
        val tvModelSize = findViewById<TextView>(R.id.tvModelSize)
        try {

            val fileDescriptor = assets.openFd(modelFileName)
            val sizeInBytes = fileDescriptor.length
            val sizeInMB = sizeInBytes / (1024.0 * 1024.0)

            tvModelSize?.text = String.format("Model Size: %.2f MB", sizeInMB)
            fileDescriptor.close()
        } catch (e: Exception) {
            tvModelSize?.text = "Model Size: Unknown"
            e.printStackTrace()
        }

        //Saved Plants Consumed
        val dbFile = getDatabasePath("HerbalPlants.db")
        val dbSizeInBytes = if (dbFile.exists()) dbFile.length() else 0L
        val dbSizeInMB = dbSizeInBytes / (1024.0 * 1024.0)

        val tvDatabaseSize = findViewById<TextView>(R.id.tvDatabaseSize)
        tvDatabaseSize?.text = String.format("Saved Plants Data: %.2f MB", dbSizeInMB)

        // 2. Get Phone Internal Storage
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        // Calculate in Double first to avoid rounding to zero
        val totalMB = (totalBlocks * blockSize).toDouble() / (1024 * 1024)
        val availableMB = (availableBlocks * blockSize).toDouble() / (1024 * 1024)
        val usedMB = totalMB - availableMB

        // Convert to GB for the text display
        val totalGB = totalMB / 1024.0
        val usedGB = usedMB / 1024.0
    }
}