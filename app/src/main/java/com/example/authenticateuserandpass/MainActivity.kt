@file:Suppress("DEPRECATION")

package com.example.authenticateuserandpass



import com.facebook.FacebookCallback
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.authenticateuserandpass.databinding.ActivityMainBinding
import com.example.authenticateuserandpass.model.User
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.facebook.FacebookException
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.firestore

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private lateinit var callbackManager: CallbackManager


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        auth.setLanguageCode("vi")
        callbackManager = CallbackManager.Factory.create()

        // nếu đã đăng nhập thì chuyển sang HomeActivity
        if (auth.currentUser != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }
        setupGoogleSignIn()
        registerGoogleSignInLauncher()
        setupClickListeners()

        // This callback is registered with LoginManager and invoked by CallbackManager
        // after it processes the result from onActivityResult.
        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(loginResult: LoginResult) {
                Log.d("FacebookLogin", "Facebook SDK onSuccess: ${loginResult.accessToken.token}")
                handleFacebookAccessTokenWithFirebase(loginResult.accessToken)
            }

            override fun onCancel() {
                Log.d("FacebookLogin", "Facebook SDK onCancel")
                Toast.makeText(this@MainActivity, "Facebook login canceled.", Toast.LENGTH_SHORT).show()
            }

            override fun onError(error: FacebookException) {
                Log.e("FacebookLogin", "Facebook SDK onError", error)
                Toast.makeText(this@MainActivity, "Facebook login error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun registerGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                binding.progressBar.visibility = View.VISIBLE
                try {
                    val account = task.getResult(ApiException::class.java)
                    account?.idToken?.let { token ->
                        val credential = GoogleAuthProvider.getCredential(token, null)
                        firebaseAuthWithGoogle(credential)
                        //binding.progressBar.visibility = View.GONE
                    } ?: showToast("Đăng nhập Google thất bại: Không có ID token")
                } catch (e: ApiException) {
                    showToast("Đăng nhập Google thất bại: ${e.localizedMessage}")
                }
            } else {
                // Handle cases where sign-in was cancelled or failed before returning a result
                showToast("Đăng nhập Google đã bị hủy hoặc thất bại.")
                binding.progressBar.visibility = View.GONE
            }

        }

    }

    private fun setupClickListeners() {
        binding.btnGoogleSignIn.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
            //val signInIntent = googleSignInClient.signInIntent
            // Consider using the new Activity Result API here
            //startActivityForResult(signInIntent, RC_SIGN_IN_1)
            //googleSignInLauncher.launch(signInIntent)
        }
        binding.btnRegister.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập email và mật khẩu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.edtEmail.error = "Địa chỉ email không hợp lệ" // Set error directly on EditText
                return@setOnClickListener
            }

            // Optional: Add password length/complexity checks
             if (password.length < 6) {
                 binding.edtPassword.error = "Mật khẩu phải có ít nhất 6 ký tự"
                 return@setOnClickListener
             }
            registerUser(email, password)
        }
        binding.btnLogin.setOnClickListener {
            val email = binding.edtEmail.text.toString().trim()
            val password = binding.edtPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập email và mật khẩu", Toast.LENGTH_SHORT).show()
            } else {
                loginUser(email, password)
            }
        }
        binding.customFbButton.setOnClickListener {
            if (AccessToken.getCurrentAccessToken() != null) {
                LoginManager.getInstance().logOut()
            }
            LoginManager.getInstance().logInWithReadPermissions(this,
                callbackManager,
                listOf("email", "public_profile"))
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun registerUser(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Đăng ký thành công", Toast.LENGTH_SHORT).show()
                    // Optionally, log in the user directly or navigate somewhere
                } else {
                    Toast.makeText(this, "Đăng ký thất bại: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    saveUserToFirestore()
                    //Toast.makeText(this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Sai tài khoản hoặc mật khẩu", Toast.LENGTH_SHORT).show()
                }
            }
    }
    // Marked as deprecated in your original code.
    // It's highly recommended to migrate to the Activity Result APIs.
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (requestCode == RC_SIGN_IN_1) {
//            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
//
//            try {
//                val account = task.getResult(ApiException::class.java)
//                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
//                firebaseAuthWithGoogle(credential)
//            } catch (e: ApiException) {
//                Toast.makeText(this, "Đăng nhập Google thất bại: ${e.message}", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }

    private fun firebaseAuthWithGoogle(credential: AuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                binding.progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    saveUserToFirestore()
                    //Toast.makeText(this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                    navigateToHome()
                    finish()
                } else {
                    showToast("Xác thực Firebase thất bại: ${task.exception?.message}")
                }
            }
    }
    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish() // Finish MainActivity so user can't go back to it
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun saveUserToFirestore() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val user = User(
            uid = currentUser.uid,
            name = currentUser.displayName ?: "", // Google login
            email = currentUser.email ?: "",
            avatarUrl = currentUser.photoUrl?.toString() ?: ""
            // phone, address sẽ để trống ban đầu
        )

        val db = Firebase.firestore
        db.collection("users").document(user.uid)
            .set(user)
            .addOnSuccessListener {
                Log.d("Firestore", "User saved successfully")
            }
            .addOnFailureListener {
                Log.e("Firestore", "Error saving user: ${it.message}")
            }
    }

    private fun handleFacebookAccessTokenWithFirebase(token: com.facebook.AccessToken) {
        if (AccessToken.getCurrentAccessToken() != null) {
            LoginManager.getInstance().logOut()
        }
        Log.d("FacebookLogin", "Firebase Auth: Handling Facebook Access Token")
        binding.progressBar.visibility = View.VISIBLE
        val credential = FacebookAuthProvider.getCredential(token.token)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                binding.progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Toast.makeText(this, "Firebase auth with Facebook successful: ${user?.displayName}", Toast.LENGTH_SHORT).show()
                    saveUserToFirestore()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Firebase auth with Facebook failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    Log.e("FacebookLogin", "Firebase auth failed", task.exception)
                }
            }
    }


}
