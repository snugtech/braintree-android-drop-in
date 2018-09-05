package com.braintreepayments.api.dropin;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.braintreepayments.api.PaymentMethod;
import com.braintreepayments.api.dropin.adapters.VaultManagerPaymentMethodsAdapter;
import com.braintreepayments.api.dropin.view.PaymentMethodItemView;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.exceptions.PaymentMethodDeleteException;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.interfaces.PaymentMethodNonceDeletedListener;
import com.braintreepayments.api.models.PaymentMethodNonce;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.braintreepayments.api.dropin.DropInActivity.EXTRA_PAYMENT_METHOD_NONCES;

public class VaultManagerActivity extends BaseActivity implements PaymentMethodNonceDeletedListener,
        BraintreeErrorListener, View.OnClickListener {

    @VisibleForTesting
    protected VaultManagerPaymentMethodsAdapter mAdapter = new VaultManagerPaymentMethodsAdapter(this);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bt_vault_management_activity);

        RecyclerView vaultManagerView = findViewById(R.id.bt_vault_manager_list);
        View closeButton = findViewById(R.id.bt_vault_manager_close);

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        try {
            mBraintreeFragment = getBraintreeFragment();
        } catch (InvalidArgumentException e) {
            finish(e);
        }

        ArrayList<PaymentMethodNonce> nonces;
        if (savedInstanceState == null) {
            nonces = getIntent().getParcelableArrayListExtra(EXTRA_PAYMENT_METHOD_NONCES);
        } else {
            nonces = savedInstanceState.getParcelableArrayList(EXTRA_PAYMENT_METHOD_NONCES);
        }

        mAdapter.setPaymentMethodNonces(nonces);

        vaultManagerView.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.VERTICAL, false));
        vaultManagerView.setAdapter(mAdapter);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(EXTRA_PAYMENT_METHOD_NONCES, mAdapter.getPaymentMethodNonces());
    }

    @Override
    public void onPaymentMethodNonceDeleted(PaymentMethodNonce paymentMethodNonce) {
        mAdapter.paymentMethodDeleted(paymentMethodNonce);

        mBraintreeFragment.sendAnalyticsEvent("manager.delete.succeeded");
        setResult(Activity.RESULT_OK, new Intent()
                .putExtra(EXTRA_PAYMENT_METHOD_NONCES, mAdapter.getPaymentMethodNonces()));
    }

    @Override
    public void onError(Exception error) {
        if(error instanceof PaymentMethodDeleteException) {
            PaymentMethodDeleteException exception = (PaymentMethodDeleteException)error;
            PaymentMethodNonce paymentMethodNonce = exception.getPaymentMethodNonce();

            mAdapter.cancelSwipeOnPaymentMethodNonce(paymentMethodNonce);

            Snackbar.make(findViewById(R.id.bt_base_view), R.string.bt_vault_manager_delete_failure,
                    Snackbar.LENGTH_LONG).show();
            mBraintreeFragment.sendAnalyticsEvent("manager.delete.failed");
        } else {
            mBraintreeFragment.sendAnalyticsEvent("manager.unknown.failed");
            finish(error);
        }
    }

    @Override
    public void onClick(View v) {
        if (v instanceof PaymentMethodItemView) {
            final AtomicBoolean positiveSelected = new AtomicBoolean(false);

            PaymentMethodItemView paymentMethodItemView = (PaymentMethodItemView)v;
            final PaymentMethodNonce paymentMethodNonceToDelete = paymentMethodItemView
                    .getPaymentMethodNonce();

            PaymentMethodItemView dialogView = new PaymentMethodItemView(this);
            dialogView.setPaymentMethod(paymentMethodNonceToDelete, false);

            new AlertDialog.Builder(VaultManagerActivity.this,
                    R.style.Theme_AppCompat_Light_Dialog_Alert)
                    .setTitle(R.string.bt_delete_confirmation_title)
                    .setMessage(R.string.bt_delete_confirmation_description)
                    .setView(dialogView)
                    .setPositiveButton(R.string.bt_delete, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            positiveSelected.set(true);
                            mBraintreeFragment.sendAnalyticsEvent("manager.delete.confirmation.positive");
                            PaymentMethod.deletePaymentMethod(mBraintreeFragment, paymentMethodNonceToDelete);
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            if (!positiveSelected.get()) {
                                mAdapter.cancelSwipeOnPaymentMethodNonce(paymentMethodNonceToDelete);
                                mBraintreeFragment.sendAnalyticsEvent("manager.delete.confirmation.negative");
                            }
                        }
                    })
                    .setNegativeButton(R.string.bt_cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create()
                    .show();
        }
    }
}
