package com.zulip.android.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.zulip.android.BuildConfig;
import com.zulip.android.R;
import com.zulip.android.util.ZLog;
import com.zulip.android.ZulipApp;
import com.zulip.android.networking.ZulipAsyncPushTask.AsyncTaskCompleteListener;
import com.zulip.android.networking.AsyncLogin;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends FragmentActivity implements View.OnClickListener,
        GoogleApiClient.OnConnectionFailedListener, CompoundButton.OnCheckedChangeListener {
    private static final int REQUEST_CODE_RESOLVE_ERR = 9000;
    private static final int REQUEST_CODE_SIGN_IN = 9001;

    private ProgressDialog connectionProgressDialog;
    private GoogleApiClient mGoogleApiClient;
    private EditText mServerEditText;
    private View mGoogleSignInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.login);

        // Progress bar to be displayed if the connection failure is not resolved.
        connectionProgressDialog = new ProgressDialog(this);
        connectionProgressDialog.setMessage(getString(R.string.signing_in));
        findViewById(R.id.google_sign_in_button).setOnClickListener(this);
        findViewById(R.id.zulip_login).setOnClickListener(this);
        ((CheckBox) findViewById(R.id.checkbox_usezulip)).setOnCheckedChangeListener(this);
        mServerEditText = (EditText) findViewById(R.id.server_url);
        mGoogleSignInButton = findViewById(R.id.google_sign_in_button);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch (requestCode) {
            case REQUEST_CODE_SIGN_IN:
                GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(intent);
                handleSignInResult(result);
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onStop() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    private void saveServerURL() {
        String serverURL = mServerEditText.getText().toString();

        // todo: add protocol if no protocol exists, default to https

        if (!serverURL.endsWith("/")) {
            serverURL = serverURL + "/";
        }

        if (!serverURL.startsWith("https://api.zulip.com/")) {
            serverURL = serverURL + "api/";
        }

        ((ZulipApp) getApplication()).setServerURL(serverURL);
    }


    private void handleSignInResult(GoogleSignInResult result) {
        Log.d("Login", "handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            GoogleSignInAccount account = result.getSignInAccount();

            // if there's a problem with fetching the account, bail
            if (account == null) {
                connectionProgressDialog.dismiss();
                Toast.makeText(LoginActivity.this, R.string.google_app_login_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            final AsyncLogin loginTask = new AsyncLogin(LoginActivity.this, "google-oauth2-token", account.getIdToken());
            loginTask.setCallback(new AsyncTaskCompleteListener() {
                @Override
                public void onTaskComplete(String result, JSONObject object) {
                    try {
                        String email = object.getString("email");
                        ((ZulipApp) getApplication()).setEmail(email);
                    } catch (JSONException e) {
                        ZLog.logException(e);
                    }
                }

                @Override
                public void onTaskFailure(String result) {
                    // Invalidate the token and try again, unless the user we
                    // are authenticating as is not registered or is disabled.
                    connectionProgressDialog.dismiss();

                }
            });
            loginTask.execute();
        } else {
            // something bad happened. whoops.
            connectionProgressDialog.dismiss();
            Toast.makeText(LoginActivity.this, R.string.google_app_login_failed, Toast.LENGTH_SHORT).show();
        }
    }

    protected void openLegal() {
        Intent i = new Intent(this, LegalActivity.class);
        startActivityForResult(i, 0);
    }

    public void openHome() {
        // Cancel before leaving activity to avoid leaking windows
        connectionProgressDialog.dismiss();
        Intent i = new Intent(this, ZulipActivity.class);
        startActivity(i);
        finish();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (connectionProgressDialog.isShowing()) {
            // The user clicked the sign-in button already. Start to resolve
            // connection errors. Wait until onConnected() to dismiss the
            // connection dialog.
            if (result.hasResolution()) {
                try {
                    result.startResolutionForResult(this, REQUEST_CODE_RESOLVE_ERR);
                } catch (SendIntentException e) {
                    // Yeah, no idea what to do here.
                    connectionProgressDialog.dismiss();
                    Toast.makeText(LoginActivity.this, R.string.google_app_login_failed, Toast.LENGTH_SHORT).show();
                }
            } else {
                connectionProgressDialog.dismiss();
                Toast.makeText(LoginActivity.this, R.string.google_app_login_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void setupGoogleSignIn() {
        if (mGoogleApiClient == null) {
            GoogleSignInOptions googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestIdToken(BuildConfig.GOOGLE_CLIENT_ID)
                    .build();

            mGoogleApiClient = new GoogleApiClient.Builder(LoginActivity.this)
                    .addApi(Auth.GOOGLE_SIGN_IN_API, googleSignInOptions)
                    .addOnConnectionFailedListener(LoginActivity.this)
                    .build();

            mGoogleApiClient.connect();
            allowUserToPickAccount();
        } else {
            allowUserToPickAccount();
        }

    }

    private void allowUserToPickAccount() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, REQUEST_CODE_SIGN_IN);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.google_sign_in_button:
                connectionProgressDialog.show();
                saveServerURL();
                setupGoogleSignIn();
                break;
            case R.id.zulip_login:
                connectionProgressDialog.show();
                saveServerURL();
                AsyncLogin alog = new AsyncLogin(LoginActivity.this, ((EditText) findViewById(R.id.username)).getText().toString(),
                        ((EditText) findViewById(R.id.password)).getText().toString());
                // Remove the CPD when done
                alog.setCallback(new AsyncTaskCompleteListener() {
                    @Override
                    public void onTaskComplete(String result, JSONObject object) {
                        connectionProgressDialog.dismiss();
                    }

                    @Override
                    public void onTaskFailure(String result) {
                        connectionProgressDialog.dismiss();
                    }

                });
                alog.execute();
                break;
            case R.id.legal_button:
                openLegal();
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            mServerEditText.setVisibility(View.GONE);
            mServerEditText.getText().clear();
            mGoogleSignInButton.setVisibility(View.VISIBLE);
        } else {
            mServerEditText.setVisibility(View.VISIBLE);
            mGoogleSignInButton.setVisibility(View.GONE);
        }
    }
}
