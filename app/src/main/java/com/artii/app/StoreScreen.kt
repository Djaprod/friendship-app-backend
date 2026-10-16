package com.artii.app
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.billingclient.api.*
import kotlinx.coroutines.launch

const val PREMIUM_PRODUCT="artii_premium_monthly"
const val BOOST_PRODUCT="artii_boost_50"
class ArtiiBilling(private val activity:Activity,private val model:ArtiiModel):PurchasesUpdatedListener{
 var status by mutableStateOf("Checking store availability…");private set
 var working by mutableStateOf(false);private set
 var enabled by mutableStateOf(false);private set
 var accountId="";private set
 val products=mutableStateMapOf<String,ProductDetails>()
 private val client=BillingClient.newBuilder(activity).setListener(this).enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).enableAutoServiceReconnection().build()
 init{model.scope.launch{try{if(model.demo){status="Sign in before purchasing.";return@launch};val info=model.api.request("/store");accountId=info.getString("accountId");if(!info.getBoolean("enabled")){status="Purchases are not activated in this test build. No payment will be taken.";return@launch};client.startConnection(object:BillingClientStateListener{override fun onBillingSetupFinished(result:BillingResult){if(result.responseCode==BillingClient.BillingResponseCode.OK){enabled=true;status="Prices and trial eligibility are confirmed by Google Play.";query(PREMIUM_PRODUCT,BillingClient.ProductType.SUBS);query(BOOST_PRODUCT,BillingClient.ProductType.INAPP);restore()}else status="Google Play billing is unavailable on this installation."};override fun onBillingServiceDisconnected(){status="Reconnecting to Google Play…"}})}catch(e:Exception){status=e.message?:"Store unavailable"}}}
 private fun query(id:String,type:String,after:((ProductDetails)->Unit)?=null){client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build())).build()){result,details->activity.runOnUiThread{val product=details.productDetailsList.firstOrNull();if(result.responseCode==BillingClient.BillingResponseCode.OK&&product!=null){products[id]=product;after?.invoke(product)}else{working=false;status="This product has not been activated for your Google Play account."}}}}
 fun offer(p:ProductDetails)=p.subscriptionOfferDetails?.filter{it.pricingPhases.pricingPhaseList.lastOrNull()?.billingPeriod=="P1M"}?.let{list->list.firstOrNull{it.pricingPhases.pricingPhaseList.any{phase->phase.priceAmountMicros==0L&&phase.billingPeriod=="P3D"}}?:list.firstOrNull()}
 fun trial()=products[PREMIUM_PRODUCT]?.let{offer(it)?.pricingPhases?.pricingPhaseList?.any{phase->phase.priceAmountMicros==0L&&phase.billingPeriod=="P3D"}}==true
 fun price(id:String)=products[id]?.let{if(id==PREMIUM_PRODUCT)offer(it)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice else it.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice}
 fun buy(id:String){if(!enabled||working)return;working=true;query(id,if(id==PREMIUM_PRODUCT)BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP){p->val selected=if(id==PREMIUM_PRODUCT)offer(p)?.offerToken else p.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken;if(selected==null){working=false;status="No eligible offer is available.";return@query};val result=client.launchBillingFlow(activity,BillingFlowParams.newBuilder().setObfuscatedAccountId(accountId).setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(p).setOfferToken(selected).build())).build());if(result.responseCode!=BillingClient.BillingResponseCode.OK){working=false;status=result.debugMessage}}}
 override fun onPurchasesUpdated(result:BillingResult,purchases:MutableList<Purchase>?){activity.runOnUiThread{working=false;if(result.responseCode==BillingClient.BillingResponseCode.OK)purchases?.forEach{process(it)}else status=if(result.responseCode==BillingClient.BillingResponseCode.USER_CANCELED)"Purchase cancelled. Your balance has not changed." else "Purchase could not complete. Try restoring purchases."}}
 private fun process(p:Purchase){if(p.purchaseState!=Purchase.PurchaseState.PURCHASED){status="Payment is pending. Chances unlock after Google Play confirms it.";return};val product=p.products.firstOrNull{it==PREMIUM_PRODUCT||it==BOOST_PRODUCT}?:return;model.scope.launch{working=true;try{val result=model.api.request("/store/verify","POST",json("product" to product,"purchaseToken" to p.purchaseToken));check(result.getBoolean("verified"));if(result.getBoolean("consume"))client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()){r,_->if(r.responseCode!=BillingClient.BillingResponseCode.OK)status="Credits saved. Restore purchases to finish confirming with Google Play."}else if(!p.isAcknowledged)client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()){r->if(r.responseCode!=BillingClient.BillingResponseCode.OK)status="Restore purchases to finish subscription confirmation."};model.refresh();status="Purchase verified. Your chances are ready."}catch(e:Exception){status=e.message?:"Verification failed. Restore purchases to retry."}finally{working=false}}}
 fun restore(){if(!enabled)return;listOf(BillingClient.ProductType.SUBS,BillingClient.ProductType.INAPP).forEach{type->client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()){result,purchases->if(result.responseCode==BillingClient.BillingResponseCode.OK)activity.runOnUiThread{purchases.forEach{process(it)}}}}}
 fun close(){client.endConnection()}
}
@Composable fun StoreScreen(m:ArtiiModel,onDismiss:()->Unit){
 val context=LocalContext.current;val billing=remember(m.api.token){ArtiiBilling(context as Activity,m)};DisposableEffect(billing){onDispose{billing.close()}}
 val balance=m.discovery.optJSONObject("balance");val premiumActive=(balance?.optLong("premiumUntil")?:0)>System.currentTimeMillis()
 Dialog(onDismissRequest={if(!billing.working)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)){
  Surface(Modifier.fillMaxSize(),color=Cream){
   Column(Modifier.artiiBackdrop().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
    TextButton(onClick=onDismiss,enabled=!billing.working){Text("← Back")};Title("Artii Premium")
    Text("Daily: "+(balance?.optInt("daily")?:10)+" / "+(balance?.optInt("cap")?:10)+"   ·   Wallet: "+(balance?.optInt("wallet")?:0),color=Coral)
    Text("One chance per completed meeting. No time limit.",fontSize=13.sp,color=Muted)
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)){
     OfferCard(Modifier.fillMaxWidth(),"Premium",if(billing.trial())"3-DAY FREE TRIAL" else if(billing.products.isEmpty())"3-day trial for eligible accounts" else "50 DAILY CHANCES",(billing.price(PREMIUM_PRODUCT)?:"US$4.00")+" / month","50 chances every 24 hours. Unused daily chances do not roll over.",if(premiumActive)"Active" else if(billing.trial())"Start free trial" else "Subscribe",!billing.working&&!premiumActive&&billing.products.containsKey(PREMIUM_PRODUCT)){billing.buy(PREMIUM_PRODUCT)}
     OfferCard(Modifier.fillMaxWidth(),"Instant Boost","NEVER EXPIRES",billing.price(BOOST_PRODUCT)?:"US$7.99","50 extra chances in your wallet. One-time purchase. Used after daily chances.","Get 50 chances",!billing.working&&billing.products.containsKey(BOOST_PRODUCT)){billing.buy(BOOST_PRODUCT)}
    }
    Text(if(billing.trial())"Free for 3 days, then "+billing.price(PREMIUM_PRODUCT)+" monthly unless cancelled. Cancel in Google Play before the trial ends to avoid being charged." else "Subscription renews monthly until cancelled in Google Play. Trial eligibility and local prices are determined by Google Play. Listed US prices are planned prices while the store is inactive.",fontSize=11.sp,color=Muted)
    if(billing.working)LinearProgressIndicator(Modifier.fillMaxWidth());Text(billing.status,fontSize=12.sp,color=Muted)
    Row{TextButton(onClick={billing.restore()},enabled=billing.enabled&&!billing.working){Text("Restore purchases")};TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/account/subscriptions")))}){Text("Manage plan")}}
    OutlinedButton(onClick=onDismiss,enabled=!billing.working,modifier=Modifier.fillMaxWidth()){Text("Back to Artii")}
   }
  }
 }
}
@Composable private fun OfferCard(modifier:Modifier,title:String,badge:String,price:String,body:String,action:String,enabled:Boolean,onClick:()->Unit){Surface(modifier.artiiPanel(26),shape=RoundedCornerShape(26.dp),color=Color.Transparent,border=BorderStroke(1.dp,if(title=="Premium")Coral else Color(0xFF339DFF))){Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(badge,color=Coral,fontSize=10.sp,fontWeight=FontWeight.Bold);Text(title,fontSize=25.sp,fontWeight=FontWeight.Bold);Text(price,fontSize=23.sp);Text(body,fontSize=12.sp,color=Muted);Button(onClick,enabled=enabled,contentPadding=PaddingValues(8.dp),modifier=Modifier.fillMaxWidth()){Text(action,fontSize=12.sp)}}}}
