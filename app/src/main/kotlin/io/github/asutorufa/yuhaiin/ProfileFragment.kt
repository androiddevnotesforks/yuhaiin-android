package io.github.asutorufa.yuhaiin

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.AppCompatEditText
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.preference.*
import com.github.logviewer.LogcatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import io.github.asutorufa.yuhaiin.database.LastProfile
import io.github.asutorufa.yuhaiin.database.Profile
import java.util.*
import java.util.regex.Pattern

class ProfileFragment : PreferenceFragmentCompat() {
    private val db = MainApplication.db.ProfileDao()
    private lateinit var mProfile: Profile

    private val refreshPreferences = ArrayList<() -> Unit>()

    private var mBinder: IVpnService? = null
    private lateinit var mFab: FloatingActionButton

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p1: ComponentName, binder: IBinder) {
            mBinder = IVpnService.Stub.asInterface(binder).also {
                if (it.isRunning) mFab.setImageResource(R.drawable.stop)
                else mFab.setImageResource(R.drawable.play_arrow)
            }
        }

        override fun onServiceDisconnected(p1: ComponentName) {
            mBinder = null
        }
    }

    private val bReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                YuhaiinVpnService.INTENT_DISCONNECTED -> {
                    Log.d(tag, "onReceive: DISCONNECTED")

                    mFab.isEnabled = true
                    mFab.setImageResource(R.drawable.play_arrow)
                    Snackbar.make(
                        requireView(),
                        "yuhaiin disconnected",
                        Snackbar.LENGTH_SHORT
                    ).setAnchorView(mFab).show()
                }
                YuhaiinVpnService.INTENT_CONNECTED -> {
                    Log.d(tag, "onReceive: CONNECTED")

                    mFab.isEnabled = true
                    mFab.setImageResource(R.drawable.stop)

                    Snackbar.make(
                        requireView(),
                        "yuhaiin connected, listen at: ${mProfile.yuhaiinPort}",
                        Snackbar.LENGTH_SHORT
                    ).setAnchorView(mFab).show()
                    context.bindService(
                        Intent(context, YuhaiinVpnService::class.java),
                        mConnection,
                        Context.BIND_AUTO_CREATE
                    )
                }
                YuhaiinVpnService.INTENT_CONNECTING -> {
                    Log.d(tag, "onReceive: CONNECTING")
                    mFab.isEnabled = false
                }
                YuhaiinVpnService.INTENT_DISCONNECTING -> {
                    Log.d(tag, "onReceive: DISCONNECTING")
                    mFab.isEnabled = false
                }

                YuhaiinVpnService.INTENT_ERROR -> {
                    Log.d(tag, "onReceive: ERROR")
                    intent.getStringExtra("message")?.let {
                        Snackbar.make(
                            requireView(),
                            it,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (mBinder == null)
            context?.bindService(
                Intent(context, YuhaiinVpnService::class.java),
                mConnection,
                Context.BIND_AUTO_CREATE
            )


        val f = IntentFilter().apply {
            addAction(YuhaiinVpnService.INTENT_DISCONNECTED)
            addAction(YuhaiinVpnService.INTENT_CONNECTED)
            addAction(YuhaiinVpnService.INTENT_CONNECTING)
            addAction(YuhaiinVpnService.INTENT_DISCONNECTING)
        }
        requireContext().registerReceiver(bReceiver, f)
    }

    override fun onCreate(saveInstanceState: Bundle?) {
        super.onCreate(saveInstanceState)
        addPreferencesFromResource(R.xml.settings)
        setHasOptionsMenu(true)
        preferenceManager.preferenceDataStore = DataStore()
        initPreferences()
        mProfile =
            db.getProfileByName(db.getLastProfile() ?: "Default") ?: Profile(name = "Default")
        reload()
    }


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mFab = container?.findViewById<FloatingActionButton>(R.id.floatingActionButton)!!.also {
            it.setOnClickListener {
                mBinder?.also { it2 ->
                    if (it2.isRunning) stopVpn()
                    else startVpn()
                } ?: startVpn()
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            requireContext().unregisterReceiver(bReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (menu is MenuBuilder) menu.setOptionalIconsVisible(true)
        inflater.inflate(R.menu.main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.prof_add -> {
                val e = AppCompatEditText(requireContext()).apply { isSingleLine = true }

                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.prof_add)
                    .setView(e)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        val name = e.text.toString().trim()
                        if (name.isEmpty()) {
                            return@setPositiveButton
                        }

                        if (db.isProfileExists(name)) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.err_add_prof),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setPositiveButton
                        }

                        mProfile = Profile(name = name)
                        db.addProfile(mProfile)
                        db.setLastProfile(LastProfile(name = name))
                        reload()
                    }
                    .setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int -> }
                    .create()
                    .show()
                true
            }
            R.id.prof_del -> {
                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.prof_del)
                    .setMessage(String.format(getString(R.string.prof_del_confirm), mProfile.name))
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        db.deleteProfile(mProfile)
                        mProfile = db.getProfileByName("Default") ?: Profile(name = "Default")
                        reload()
                    }
                    .setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int -> }
                    .create()
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initPreferences() {
        findPreference<ListPreference>(resources.getString(R.string.profile_key))!!.also {
            refreshPreferences.add {
                it.value = mProfile.name
                val profiles = db.getProfileNames()
                it.entries = profiles.toTypedArray()
                it.entryValues = profiles.toTypedArray()
            }

            setOnPreferenceChangeListener(it) { _, newValue ->
                val name = newValue.toString()
                mProfile = db.getProfileByName(name) ?: Profile(name = name)
                db.setLastProfile(LastProfile(name = name))
                reload()
            }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.yuhaiin_port_key))!!.also {
            it.setOnBindEditTextListener { editText: EditText ->
                editText.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.yuhaiinPort = newValue.toString().toInt()
            }

            refreshPreferences.add { it.text = mProfile.yuhaiinPort.toString() }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.http_server_port_key))!!.also {
            it.setOnBindEditTextListener { editText: EditText ->
                editText.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.httpServerPort = newValue.toString().toInt()
            }

            refreshPreferences.add { it.text = mProfile.httpServerPort.toString() }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.socks5_server_port_key))!!.also {
            it.setOnBindEditTextListener { editText: EditText ->
                editText.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.socks5ServerPort = newValue.toString().toInt()
            }

            refreshPreferences.add { it.text = mProfile.socks5ServerPort.toString() }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.auth_userpw_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.isUserPw = newValue as Boolean
                reload()
            }
            refreshPreferences.add { it.isChecked = mProfile.isUserPw }
        }
        findPreference<EditTextPreference>(resources.getString(R.string.auth_username_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.username = newValue.toString()
            }
            refreshPreferences.add {
                it.text = mProfile.username
                it.isVisible = mProfile.isUserPw
            }
        }
        findPreference<EditTextPreference>(resources.getString(R.string.auth_password_key))!!.also {
            it.setOnBindEditTextListener { editText: EditText ->
                editText.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.password = newValue.toString()
            }
            refreshPreferences.add {
                it.text = mProfile.password
                it.isVisible = mProfile.isUserPw
            }
        }

        findPreference<DropDownPreference>(resources.getString(R.string.adv_route_Key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.route = newValue as String
            }
            refreshPreferences.add { it.value = mProfile.route }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.adv_fake_dns_cidr_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.fakeDnsCidr = newValue.toString()
            }
            refreshPreferences.add { it.text = mProfile.fakeDnsCidr }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.adv_dns_port_key))!!.also {
            it.setOnBindEditTextListener { editText: EditText ->
                editText.inputType =
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.dnsPort = newValue.toString().toInt()
            }

            refreshPreferences.add { it.text = mProfile.dnsPort.toString() }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.adv_per_app_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.isPerApp = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = mProfile.isPerApp }
        }
        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.adv_app_bypass_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.isBypassApp = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = mProfile.isBypassApp }
        }
        findPreference<MultiSelectListPreference>(resources.getString(R.string.adv_app_list_key))!!.also {
            it.setOnPreferenceClickListener { _ ->
                updateAppList(it)
                false
            }

            @Suppress("Unchecked_Cast")
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.appList = newValue as Set<String>
            }

            refreshPreferences.add { it.values = mProfile.appList }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.ipv6_proxy_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.hasIPv6 = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = mProfile.hasIPv6 }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.adv_auto_connect_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.autoConnect = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = mProfile.autoConnect }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.allow_lan_key))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.allowLan = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = mProfile.allowLan }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.rule_proxy))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.ruleProxy = newValue.toString()
            }
            refreshPreferences.add { it.text = mProfile.ruleProxy }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.rule_direct))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.ruleDirect = newValue.toString()
            }
            refreshPreferences.add { it.text = mProfile.ruleDirect }
        }

        findPreference<EditTextPreference>(resources.getString(R.string.rule_block))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.ruleBlock = newValue.toString()
            }
            refreshPreferences.add { it.text = mProfile.ruleBlock }
        }

        findPreference<SwitchPreferenceCompat>(resources.getString(R.string.save_logcat))!!.also {
            setOnPreferenceChangeListener(it) { _, newValue ->
                mProfile.saveLogcat = newValue as Boolean
            }
            refreshPreferences.add { it.isChecked = mProfile.saveLogcat }
        }

        findPreference<Preference>(resources.getString(R.string.logcat))?.apply {
            setOnPreferenceClickListener {
                val logcatExcludeRules = listOf(
                    Pattern.compile(".*]: processMotionEvent MotionEvent \\{ action=ACTION_.*"),
                    Pattern.compile(".*]: dispatchPointerEvent handled=true, event=MotionEvent \\{ action=ACTION_.*"),
                    Pattern.compile(".*Davey! duration=.*")
                )
                LogcatActivity.start(context, logcatExcludeRules)
                true
            }
        }

        findPreference<Preference>(resources.getString(R.string.open_yuhaiin_page))?.apply {
            setOnPreferenceClickListener {
                if (mBinder == null || mBinder?.isRunning == false) {
                    Snackbar.make(
                        requireView(),
                        "yuhaiin is not running",
                        Snackbar.LENGTH_SHORT
                    ).setAnchorView(mFab).show()
                    return@setOnPreferenceClickListener true
                }

                CustomTabsIntent.Builder().apply {
                    setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                    setStartAnimations(
                        requireContext(),
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                    )
                    setExitAnimations(
                        requireContext(),
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right
                    )
                }.build().launchUrl(
                    requireContext(),
                    Uri.parse("http://127.0.0.1:${mProfile.yuhaiinPort}")
                )
                true
            }
        }
    }

    private fun setOnPreferenceChangeListener(
        it: Preference,
        run: (p: Preference, newValue: Any) -> Unit
    ) {
        it.setOnPreferenceChangeListener { preference, newValue ->
            run(preference, newValue)
            db.updateProfile(profile = mProfile)
            true
        }
    }

    private fun reload() {
        for (refresh in refreshPreferences) refresh()
    }

    private fun updateAppList(mPrefAppList: MultiSelectListPreference) {
        val titles = mutableListOf<String>()
        val packageNames = mutableListOf<String>()
        var index = 0

        for ((key, value) in packages.toList().sortedBy { it.second }.toMap()) {
            if (mProfile.appList.contains(key)) {
                packageNames.add(index, key)
                titles.add(index, value)
                index++
            } else {
                packageNames.add(key)
                titles.add(value)
            }
        }

        mPrefAppList.values = mProfile.appList
        mPrefAppList.entries = titles.toTypedArray()
        mPrefAppList.entryValues = packageNames.toTypedArray()
    }

    private val PackageInfo.hasInternetPermission: Boolean
        get() {
            val permissions = requestedPermissions
            return permissions?.any { it == Manifest.permission.INTERNET } ?: false
        }

    private val packages: Map<String, String>
        get() {
            val packageManager = requireContext().packageManager
            val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val apps = TreeMap<String, String>()

            for (pkg in packages) {
                if (!pkg.hasInternetPermission && pkg.packageName != "android") continue

                val applicationInfo = pkg.applicationInfo

                val appName = applicationInfo.loadLabel(packageManager).toString()
                // val appIcon = applicationInfo.loadIcon(packageManager)
                // val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) > 0

                apps[pkg.packageName] = "$appName\n${pkg.packageName}"
            }


            return apps
        }

    // You can do the assignment inside onAttach or onCreate, i.e, before the activity is displayed
    private var startVpnLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) startVpn(requireActivity())
        }

    private fun startVpn() =
        VpnService.prepare(activity)?.also { startVpnLauncher.launch(it) } ?: startVpn(
            requireActivity(),
        )

    private fun startVpn(context: Context) = ContextCompat.startForegroundService(
        context,
        Intent(context, YuhaiinVpnService::class.java)
    )

    private fun stopVpn() = mBinder?.stop()


    private class DataStore : PreferenceDataStore() {
        private val store = Collections.synchronizedMap(HashMap<String, Any>())
        private fun put(key: String?, value: Any?) {
            store[key] = value
        }

        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: Set<String?>?) = put(key, values)
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)

        override fun getString(key: String?, defValue: String?): String? =
            store[key]?.toString() ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: Set<String?>?): Set<String?>? =
            store[key] as Set<String?>? ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            store[key]?.toString()?.toInt() ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            store[key]?.toString()?.toLong() ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            store[key]?.toString()?.toFloat() ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            store[key]?.toString()?.toBoolean() ?: defValue
    }
}