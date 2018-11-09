package c.acdi.master.jderamaix.suaps;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.IOException;
import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class RFIDActivity extends AppCompatActivity {

    private ArrayList<String> donnees;

    public static final String PUBLIC_STATIC_STRING_IDENTIFIER = "PUBLIC_STATIC_STRING_IDENTIFIER" ;
    private NfcAdapter nfcAdapter;

    public String TAG = "RFIDActivity";

    public ArrayList<String> getdonnees() {
        return donnees;
    }

    public void setDonnees(ArrayList<String> donnes){
        this.donnees = donnes;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rfid_activite);

        ArrayList<String> var_donnees = new ArrayList<String>();
        this.setDonnees(var_donnees);

        // initialize NFCAdapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        if(nfcAdapter==null){ //Si nfcAdapter est null alors il n'y a pas de materiel NFC dans le téléphone
            Toast.makeText(this,"NFC non présent sur le téléphone, fermeture de l'application ",Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if(!nfcAdapter.isEnabled()){ //Si le NFC n'est pas activé alors on ouvre le menu pour
                                     //que l'utilisateur puisse activer le module NFC
            Toast.makeText(this,"Il faut activer le NFC",Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
        }
    }

    /**
     * Méthode permettant de mettre en place la reception d'une lecture NFC
     * la methode enableForegroundDispatch permet de dire que l'Activité doit être au premier plan
     * pour lire une carte NFC.
     */
    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = new Intent(this,RFIDActivity.class);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        nfcAdapter.enableForegroundDispatch(this,pendingIntent, null,null);
    }


    /**
     * Quand une carte est lue et détectée cette méthode est lancé
     * @param intent Contient les éléments de la carte NFC
     */
    @Override
    protected void onNewIntent(Intent intent) {

        super.onNewIntent(intent);
        new RFIDActivity.TraitementAsynchrone().execute(intent); //Pour ne pas bloquer l'interface
        //utilisateur, le traitement est lancé en tâche de fond.
        //Une tâche asynchrone est lancé avec un l'intent de la carte en argument
    }


    /**
     * Désactive l'option enableForegroundDispatch
     * Il est nécessaire de le faire lorsque l'application est mise en pause.
     */
    @Override
    protected void onPause() {;
        super.onPause();
        nfcAdapter.disableForegroundDispatch(this);
    }

    /**
     * Méthode servant à récupérer l'identifiant d'une carte étudiante
     *
     * @param tag Il s'agit ici de l'identifiant de lecture de la carte qui va être
     *            utilisé pour créer l'identifiant unique
     * @return L'identifiant unique de la carte
     */
    private String dumpTagData(Tag tag) {
        byte[] id = tag.getId();;
        return toReversedHex(id);
    }

    /**
     * Méthode calculant l'identifiant d'une carte étudiante
     *
     * @param bytes id de lecture de la carte converti en octet
     * @return l'identifiant de la carte qui correspond au code hexadéciaml inversé.
     */
    private String toReversedHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; ++i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
        }
        return sb.toString();
    }

    /**
     * Méthode executé lors du postExecute de la tâche asynchrone
     * On envoie la requête informant la base de données que quelqu'un à badger avec son numéro de carte = s
     * et on l'ajoute à l'array
     *
     * @param s est l'id de la carte étudiant
     */
    protected void envoi(String s){

        final TextView textView = (TextView) findViewById(R.id.textViewRFIDActivity);

        this.getdonnees().add(s);
        Client client = ServiceGenerator.createService(Client.class);

        NomIDCarteEtudiant carteEtudiant = new NomIDCarteEtudiant(s);

        Call<NomIDCarteEtudiant> call_Post = client.EnvoieNumCarte("badgeage/" + s,carteEtudiant);


        call_Post.enqueue(new Callback<NomIDCarteEtudiant>() {
            @Override
            public void onResponse(Call<NomIDCarteEtudiant> call, Response<NomIDCarteEtudiant> response) {

                int statusCode = response.code();
                if (response.isSuccessful()) {
                    String Result = response.body().getString();
                    textView.setText(Result);
                    Toast.makeText(RFIDActivity.this,Result,Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "status Code: " + statusCode);
                }
            }
            @Override
            public void onFailure(Call<NomIDCarteEtudiant> call, Throwable t) {
                ServiceGenerator.Message(RFIDActivity.this, TAG, t);
            }
        });
    }


    public class TraitementAsynchrone extends AsyncTask<Intent,Void,String> {

        /**
         * Traitement qui se fait dans un thread de tâche de fond, afin que le thread de
         * l'interface utilisateur soit toujours utilisable
         *
         * @param intents Contient l'intent de la carte
         * @return l'id de la carte étudiante
         */
        @Override
        protected String doInBackground(Intent... intents) {

            Intent intent = intents[0]; //récupération de l'intent

            Tag tagId =  intent.getParcelableExtra(NfcAdapter.EXTRA_TAG); //Récupération du Tag permettant d'avoir l'identifiant

            String idEtudiant = dumpTagData(tagId);
            //Log.e("Identifiant carte Etu",idEtudiant);

            return idEtudiant; //le return permet d'aller dans la méthode onPostExecute
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            envoi(s); //ici la méthode envoi est un exemple mais ici est l'endroit où il faut
            //effectuer des actions une fois le traitement terminé.
        }
    }

}
