package project.passwordproject.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import project.passwordproject.R;
import project.passwordproject.classes.DatabaseAdapter;
import project.passwordproject.classes.Site;
import project.passwordproject.classes.SiteList;
import project.passwordproject.classes.SitesAdapter;
import project.passwordproject.classes.SyncAccountsTask;
import project.passwordproject.classes.UploadToCloudTask;
import project.passwordproject.classes.Utilities;

public class ListActivity extends AppCompatActivity {

    private int REQUEST_ADD = 10;

    private FloatingActionButton fab;
    public static List<Site> mySites = null;
    private ListView sitesListView;
    private SitesAdapter sitesAdapter;
    private SharedPreferences preferences;
    private FirebaseStorage storage;
    private StorageReference mainStorageReference = null;
    private String username;
    public boolean firstStart = true;
    private DatabaseAdapter databaseAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.full_list_activity);

        username = getIntent().getStringExtra("username");

        storage = FirebaseStorage.getInstance();
        mainStorageReference = storage.getReference("pdmproject-6f9ad.appspot.com");

        databaseAdapter = new DatabaseAdapter(getApplicationContext(), username);
        databaseAdapter.openConnection();

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (savedInstanceState != null) {
            SiteList sites = (SiteList) savedInstanceState.getSerializable("CurrentSites");
            mySites = sites.getSites();
            firstStart = false;
        } else {
            mySites = new ArrayList<>();
        }


//        mySites.add(new Site("Site1", "URL1", new ArrayList<AccountDetails>()));
//        mySites.add(new Site("Site3", "URL1", new ArrayList<AccountDetails>()));

        sitesListView = (ListView) findViewById(R.id.sitesListView);
        sitesAdapter = new SitesAdapter(ListActivity.this, R.layout.site_row, mySites, databaseAdapter);


        sitesListView.setAdapter(sitesAdapter);

        sitesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(ListActivity.this, SiteDetailsActivity.class);
                intent.putExtra("SitePosition", position);
                intent.putExtra("UserName", username);
                startActivity(intent);
            }
        });

        if (firstStart) {
            try {
                mySites = databaseAdapter.restoreData();
                sitesAdapter.refreshSites(mySites);
            } catch (Exception e) {
                Toast.makeText(this, "A problem was encountered while accessing data from local database...", Toast.LENGTH_SHORT).show();
            }
        }

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ListActivity.this, AddListItem.class);
                startActivityForResult(intent, REQUEST_ADD);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        SiteList siteList = new SiteList(mySites);
        outState.putSerializable("CurrentSites", siteList);
        super.onSaveInstanceState(outState);

    }

    @Override
    protected void onStart() {
        super.onStart();

        sitesAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        databaseAdapter.closeConnection();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ADD && resultCode == RESULT_OK) {
            Site newSite = (Site) data.getSerializableExtra("newSite");
            for (Site s : sitesAdapter.getData()) {
                if (s.getName().equals(newSite.getName())) {
                    Toast.makeText(this, "You already have a site with this name added...", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            sitesAdapter.add(newSite);
            mySites = sitesAdapter.getData();
            databaseAdapter.insertSite(newSite);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_get_from_db:

                AlertDialog.Builder builder = new AlertDialog.Builder(ListActivity.this);
                builder.setMessage("You will replace all the data you have with the contents of the database... Are you sure?")
                        .setCancelable(false)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                mySites = databaseAdapter.restoreData();
                                sitesAdapter.refreshSites(mySites);
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
                return true;
            case R.id.menu_send_to_db:
                databaseAdapter.saveData(sitesAdapter.getData());
                return true;
            case R.id.menu_log_out:
                FirebaseAuth.getInstance().signOut();
                logOutListener();
                Intent i = new Intent(getApplicationContext(), LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
                finish();
                return true;
            case R.id.menu_sync_accounts:
                try {
                    syncData();
                } catch (Exception e) {
                    Toast.makeText(this, "A problem was encountered while accessing cloud data...", Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.menu_upload_cloud:
                try {
                    String xmlText = Utilities.createXml(mySites);
                    UploadToCloudTask uploadToCloudTask = new UploadToCloudTask(mainStorageReference, ListActivity.this, username);
                    uploadToCloudTask.execute(xmlText);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return true;
            case R.id.menu_pie_chart:
                Intent intent = new Intent(getApplicationContext(), PieChartActivity.class);
                SiteList list = new SiteList(sitesAdapter.getData());
                intent.putExtra("SiteList", list);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void logOutListener() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("stateDetector", false);
        editor.apply();
    }

    public void syncData() {
        AlertDialog.Builder builder = new AlertDialog.Builder(ListActivity.this);
        builder.setMessage("By proceeding you will replace the data on your app with data from cloud storage, but it won't replace the data in your local database... Are you sure?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        SyncAccountsTask syncAccountsTask = new SyncAccountsTask(ListActivity.this, mainStorageReference, sitesAdapter);
                        syncAccountsTask.execute(username);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();

    }

}
