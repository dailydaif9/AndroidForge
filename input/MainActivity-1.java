package com.alamalmukhtabirat.v6;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.net.Uri;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import android.webkit.JavascriptInterface;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;

public class MainActivity extends Activity {
    private StoreDb db;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_CHOOSER_REQUEST = 7101;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new StoreDb();
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "اختر شعار المؤسسة"), FILE_CHOOSER_REQUEST);
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Store");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private class StoreDb extends SQLiteOpenHelper {
        StoreDb() { super(MainActivity.this, "alam_store_v5.db", null, 3); }
        @Override public void onCreate(SQLiteDatabase d) {
            d.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,code TEXT,category TEXT,unit TEXT,qty REAL DEFAULT 0,min_qty REAL DEFAULT 0,lot TEXT,expiry TEXT,buy REAL DEFAULT 0,sell REAL DEFAULT 0)");
            d.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT,address TEXT,notes TEXT)");
            d.execSQL("CREATE TABLE suppliers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT,address TEXT,notes TEXT)");
            d.execSQL("CREATE TABLE invoices(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,party TEXT NOT NULL,item_id INTEGER,item_name TEXT,qty REAL,price REAL,total REAL,paid REAL,balance REAL,note TEXT,created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
            d.execSQL("CREATE TABLE invoice_lines(id INTEGER PRIMARY KEY AUTOINCREMENT,invoice_id INTEGER NOT NULL,item_id INTEGER NOT NULL,item_name TEXT NOT NULL,qty REAL NOT NULL,price REAL NOT NULL,line_total REAL NOT NULL)");
            d.execSQL("CREATE TABLE vouchers(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,party TEXT NOT NULL,amount REAL NOT NULL,note TEXT,created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
        }
        @Override public void onUpgrade(SQLiteDatabase d,int oldV,int newV) {
            if(oldV<2) d.execSQL("CREATE TABLE IF NOT EXISTS vouchers(id INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT NOT NULL,party TEXT NOT NULL,amount REAL NOT NULL,note TEXT,created_at TEXT DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    private class Bridge {
        @JavascriptInterface public String list(String table) {
            if (!table.equals("items") && !table.equals("customers") && !table.equals("suppliers") && !table.equals("invoices") && !table.equals("invoice_lines") && !table.equals("vouchers")) return "[]";
            JSONArray a = new JSONArray();
            Cursor c = db.getReadableDatabase().rawQuery("SELECT * FROM " + table + " ORDER BY id DESC", null);
            try {
                while (c.moveToNext()) {
                    JSONObject o = new JSONObject();
                    for (int i=0;i<c.getColumnCount();i++) {
                        String key=c.getColumnName(i); int type=c.getType(i);
                        Object val=type==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):type==Cursor.FIELD_TYPE_FLOAT?c.getDouble(i):type==Cursor.FIELD_TYPE_NULL?JSONObject.NULL:c.getString(i);
                        o.put(key,val);
                    }
                    a.put(o);
                }
            } catch(Exception ignored) {} finally { c.close(); }
            return a.toString();
        }

        @JavascriptInterface public boolean addItem(String name,String code,String category,String unit,String qty,String minQty,String lot,String expiry,String buy,String sell) {
            if(name==null||name.trim().isEmpty()) return false;
            ContentValues v=new ContentValues(); v.put("name",name);v.put("code",code);v.put("category",category);v.put("unit",unit);
            v.put("qty",number(qty));v.put("min_qty",number(minQty));v.put("lot",lot);v.put("expiry",expiry);v.put("buy",number(buy));v.put("sell",number(sell));
            return db.getWritableDatabase().insert("items",null,v)!=-1;
        }

        @JavascriptInterface public boolean updateItem(String id,String name,String code,String category,String unit,String qty,String minQty,String lot,String expiry,String buy,String sell) {
            if(name==null||name.trim().isEmpty()) return false;
            ContentValues v=new ContentValues();v.put("name",name);v.put("code",code);v.put("category",category);v.put("unit",unit);
            v.put("qty",number(qty));v.put("min_qty",number(minQty));v.put("lot",lot);v.put("expiry",expiry);v.put("buy",number(buy));v.put("sell",number(sell));
            return db.getWritableDatabase().update("items",v,"id=?",new String[]{id})>0;
        }

        @JavascriptInterface public boolean addPerson(String table,String name,String phone,String address,String notes) {
            if((!table.equals("customers")&&!table.equals("suppliers"))||name==null||name.trim().isEmpty()) return false;
            ContentValues v=new ContentValues();v.put("name",name);v.put("phone",phone);v.put("address",address);v.put("notes",notes);
            return db.getWritableDatabase().insert(table,null,v)!=-1;
        }

        @JavascriptInterface public String saveInvoice(String kind,String party,String linesJson,String paidText,String note) {
            if((!kind.equals("بيع")&&!kind.equals("شراء")&&!kind.equals("عينة")&&!kind.equals("صرف مخزني"))||party==null||party.trim().isEmpty()) return "أدخل اسم العميل أو المورد";
            JSONArray lines;
            double paid;
            try { lines=new JSONArray(linesJson); paid=Double.parseDouble(paidText); }
            catch(Exception e){ return "تحقق من الأصناف والدفعة"; }
            if(lines.length()==0) return "أضف صنفاً واحداً على الأقل";
            if(paid<0) return "المبلغ المدفوع لا يمكن أن يكون سالباً";
            SQLiteDatabase d=db.getWritableDatabase(); d.beginTransaction();
            try {
                double total=0, totalQty=0;
                JSONArray checked=new JSONArray(); HashMap<Long,Double> remainingStock=new HashMap<>();
                for(int i=0;i<lines.length();i++) {
                    JSONObject line=lines.getJSONObject(i);
                    long iid=line.getLong("itemId"); double qty=line.getDouble("qty"), price=line.getDouble("price");
                    if(qty<=0||price<0) return "تحقق من كمية وسعر كل صنف";
                    Cursor c=d.rawQuery("SELECT name,qty FROM items WHERE id=?",new String[]{String.valueOf(iid)});
                    if(!c.moveToFirst()){c.close();return "أحد الأصناف لم يعد موجوداً";}
                    String itemName=c.getString(0); double stock=c.getDouble(1); c.close();
                    if(remainingStock.containsKey(iid)) stock=remainingStock.get(iid);
                    if((kind.equals("بيع")||kind.equals("عينة")||kind.equals("صرف مخزني")) && qty>stock) return "المخزون غير كافٍ للصنف: "+itemName+" (المتاح "+stock+")";
                    JSONObject x=new JSONObject(); x.put("itemId",iid);x.put("itemName",itemName);x.put("qty",qty);x.put("price",price);x.put("lineTotal",qty*price);x.put("stock",stock);checked.put(x);
                    remainingStock.put(iid,(kind.equals("بيع")||kind.equals("عينة")||kind.equals("صرف مخزني"))?stock-qty:stock+qty);
                    total+=qty*price; totalQty+=qty;
                }
                if(paid>total) return "الدفعة أكبر من إجمالي الفاتورة";
                boolean nonSaleIssue=kind.equals("عينة")||kind.equals("صرف مخزني");
                double financialTotal=nonSaleIssue?0:total;
                ContentValues inv=new ContentValues(); inv.put("kind",kind);inv.put("party",party);inv.putNull("item_id");inv.put("item_name",checked.length()+" أصناف");
                inv.put("qty",totalQty);inv.put("price",0);inv.put("total",financialTotal);inv.put("paid",paid);inv.put("balance",financialTotal-paid);inv.put("note",note);
                long invoiceId=d.insert("invoices",null,inv); if(invoiceId==-1) return "تعذر حفظ الفاتورة";
                for(int i=0;i<checked.length();i++) {
                    JSONObject x=checked.getJSONObject(i); ContentValues line=new ContentValues();
                    line.put("invoice_id",invoiceId);line.put("item_id",x.getLong("itemId"));line.put("item_name",x.getString("itemName"));
                    line.put("qty",x.getDouble("qty"));line.put("price",x.getDouble("price"));line.put("line_total",x.getDouble("lineTotal"));
                    if(d.insert("invoice_lines",null,line)==-1) return "تعذر حفظ تفاصيل الفاتورة";
                    double stock=x.getDouble("stock"), qty=x.getDouble("qty"); ContentValues update=new ContentValues();
                    update.put("qty",(kind.equals("بيع")||kind.equals("عينة")||kind.equals("صرف مخزني"))?stock-qty:stock+qty);
                    d.update("items",update,"id=?",new String[]{String.valueOf(x.getLong("itemId"))});
                }
                d.setTransactionSuccessful();
                return "تم حفظ فاتورة "+kind+" رقم "+invoiceId+" بنجاح. عدد الأصناف: "+checked.length()+". الإجمالي: "+total+"، المتبقي: "+(total-paid);
            } catch(Exception e){ return "حدث خطأ أثناء حفظ الفاتورة"; }
            finally { d.endTransaction(); }
        }

        @JavascriptInterface public void printHtml(String html) {
            runOnUiThread(() -> {
                WebView printWeb = new WebView(MainActivity.this);
                printWeb.getSettings().setJavaScriptEnabled(false);
                printWeb.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView view, String url) {
                        PrintManager pm=(PrintManager)getSystemService(Context.PRINT_SERVICE);
                        PrintDocumentAdapter adapter=view.createPrintDocumentAdapter("AlamAlMukhtabirat-Invoice");
                        pm.print("فاتورة عالم المختبرات",adapter,new android.print.PrintAttributes.Builder().setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4).setResolution(new android.print.PrintAttributes.Resolution("pdf","pdf",300,300)).setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS).build());
                    }
                });
                printWeb.loadDataWithBaseURL(null,html,"text/HTML","UTF-8",null);
            });
        }

        @JavascriptInterface public boolean addVoucher(String kind,String party,String amountText,String note) {
            if((!"قبض".equals(kind)&&!"صرف".equals(kind))||party==null||party.trim().isEmpty()) return false;
            double amount=number(amountText); if(amount<=0) return false;
            ContentValues v=new ContentValues();v.put("kind",kind);v.put("party",party);v.put("amount",amount);v.put("note",note);
            return db.getWritableDatabase().insert("vouchers",null,v)!=-1;
        }

        @JavascriptInterface public void shareHtml(String html,String fileName) {
            runOnUiThread(() -> {
                final WebView pdfView = new WebView(MainActivity.this);
                pdfView.getSettings().setJavaScriptEnabled(true);
                pdfView.getSettings().setDomStorageEnabled(true);
                pdfView.setBackgroundColor(Color.WHITE);
                pdfView.setWebViewClient(new WebViewClient(){
                    private boolean finished = false;
                    @Override public void onPageFinished(WebView view,String url){
                        if (finished) return;
                        view.evaluateJavascript(
                            "(function(){var imgs=[].slice.call(document.images);return JSON.stringify({height:Math.max(document.body.scrollHeight,document.documentElement.scrollHeight),images:imgs.map(function(i){return {complete:i.complete,naturalWidth:i.naturalWidth}})});})()",
                            result -> {
                                if (finished) return;
                                finished = true;
                                view.postDelayed(() -> createAndSharePdf(view, html, fileName), 350);
                            }
                        );
                    }
                });
                pdfView.loadDataWithBaseURL("file:///android_asset/",html,"text/html","UTF-8",null);
            });
        }

        private void createAndSharePdf(WebView view, String html, String fileName) {
            try {
                int width = 595;
                view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
                int measuredHeight = Math.max(view.getMeasuredHeight(), view.getContentHeight());
                int height = Math.max(842, measuredHeight);
                view.layout(0, 0, width, height);
                PdfDocument pdf = new PdfDocument();
                int pageHeight = 842;
                int pages = Math.max(1, (int)Math.ceil(height / (double)pageHeight));
                for (int i = 0; i < pages; i++) {
                    PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(width, pageHeight, i + 1).create());
                    Canvas canvas = page.getCanvas();
                    canvas.drawColor(Color.WHITE);
                    canvas.save();
                    canvas.translate(0, -i * pageHeight);
                    view.draw(canvas);
                    canvas.restore();
                    pdf.finishPage(page);
                }
                File dir = new File(getCacheDir(), "shared");
                if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("تعذر إنشاء مجلد المشاركة");
                String safe = (fileName == null ? "document" : fileName).replaceAll("[^A-Za-z0-9_-]", "_");
                File out = new File(dir, safe + ".pdf");
                try (FileOutputStream stream = new FileOutputStream(out)) { pdf.writeTo(stream); }
                pdf.close();
                if (!out.exists() || out.length() < 500) throw new java.io.IOException("ملف PDF الناتج فارغ");
                Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".provider", out);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("application/pdf");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.setClipData(android.content.ClipData.newUri(getContentResolver(), "فاتورة عالم المختبرات", uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, "مشاركة ملف PDF"));
            } catch (Exception e) {
                // Use the enclosing Activity context; "this" here refers to Bridge.
                android.widget.Toast.makeText(MainActivity.this, "تعذرت مشاركة الفاتورة: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            } finally {
                view.destroy();
            }
        }

        @JavascriptInterface public String stats() {
            SQLiteDatabase d=db.getReadableDatabase(); JSONObject o=new JSONObject();
            try {
                o.put("items",count(d,"items"));o.put("customers",count(d,"customers"));o.put("suppliers",count(d,"suppliers"));
                Cursor low=d.rawQuery("SELECT COUNT(*) FROM items WHERE qty<=min_qty",null);if(low.moveToFirst())o.put("low",low.getInt(0));low.close();
                Cursor c=d.rawQuery("SELECT COALESCE(SUM(qty*buy),0) FROM items",null);if(c.moveToFirst())o.put("stockValue",c.getDouble(0));c.close();
                Cursor s=d.rawQuery("SELECT COALESCE(SUM(total),0),COALESCE(SUM(paid),0),COALESCE(SUM(balance),0) FROM invoices WHERE kind='بيع'",null);
                if(s.moveToFirst()){o.put("salesTotal",s.getDouble(0));o.put("salesPaid",s.getDouble(1));o.put("salesDue",s.getDouble(2));}s.close();
                Cursor p=d.rawQuery("SELECT COALESCE(SUM(total),0),COALESCE(SUM(balance),0) FROM invoices WHERE kind='شراء'",null);
                if(p.moveToFirst()){o.put("purchaseTotal",p.getDouble(0));o.put("purchaseDue",p.getDouble(1));}p.close();
            }catch(Exception ignored){}
            return o.toString();
        }
        private int count(SQLiteDatabase d,String t){Cursor c=d.rawQuery("SELECT COUNT(*) FROM "+t,null);int n=0;if(c.moveToFirst())n=c.getInt(0);c.close();return n;}
        private double number(String s){try{return Double.parseDouble(s);}catch(Exception e){return 0;}}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==FILE_CHOOSER_REQUEST && fileCallback!=null){Uri result=(resultCode==RESULT_OK&&data!=null)?data.getData():null;fileCallback.onReceiveValue(result==null?null:new Uri[]{result});fileCallback=null;}
    }
    @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
}
