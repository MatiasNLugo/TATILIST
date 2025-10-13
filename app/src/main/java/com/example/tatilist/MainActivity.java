package com.example.tatilist;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.database.*;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import android.text.Editable;
import android.text.TextWatcher;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private TaskAdapter taskAdapter;
    private FloatingActionButton fabAddTask;
    private TabLayout tabLayout;
    private TextView tvTotalExpenses, tvSharedWith;
    private Button btnShareList, btnJoinList;
    private List<Task> allTasks;
    private DatabaseReference databaseRef;
    private String currentListId;
    private SharedPreferences prefs;
    private ValueEventListener tasksListener;

    private static final int CALENDAR_PERMISSION_CODE = 101;
    private static final String CHANNEL_ID = "TATILIST_CHANNEL";

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        createNotificationChannel();
        prefs = getSharedPreferences("TATILIST_PREFS", MODE_PRIVATE);
        currentListId = prefs.getString("listId", null);
        if (currentListId == null) {
            currentListId = UUID.randomUUID().toString();
            prefs.edit().putString("listId", currentListId).apply();
        }
        databaseRef = FirebaseDatabase.getInstance().getReference();
        allTasks = new ArrayList<>();
        initViews();
        setupFirebaseListener();
        setupTabLayout();
    }

    @SuppressLint("SetTextI18n")
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        fabAddTask = findViewById(R.id.fabAddTask);
        tabLayout = findViewById(R.id.tabLayout);
        tvTotalExpenses = findViewById(R.id.tvTotalExpenses);
        tvSharedWith = findViewById(R.id.tvSharedWith);
        btnShareList = findViewById(R.id.btnShareList);
        btnJoinList = findViewById(R.id.btnJoinList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        taskAdapter = new TaskAdapter(allTasks, this);
        recyclerView.setAdapter(taskAdapter);
        tvSharedWith.setText(MessageFormat.format("Lista ID: {0}...", currentListId.substring(0, 8)));
        fabAddTask.setOnClickListener(v -> showAddTaskDialog());
        btnShareList.setOnClickListener(v -> shareListId());
        btnJoinList.setOnClickListener(v -> showJoinListDialog());
    }

    private void setupFirebaseListener() {
        tasksListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allTasks.clear();
                for (DataSnapshot taskSnapshot : snapshot.child("lists").child(currentListId)
                        .child("tasks").getChildren()) {
                    Task task = taskSnapshot.getValue(Task.class);
                    if (task != null) {
                        task.setFirebaseKey(taskSnapshot.getKey());
                        allTasks.add(task);
                    }
                }
                Collections.sort(allTasks, (t1, t2) -> {
                    if (t1.getDueDate() == null) return 1;
                    if (t2.getDueDate() == null) return -1;
                    return Long.compare(t1.getDueDate(), t2.getDueDate());
                });
                taskAdapter.notifyDataSetChanged();
                updateTotalExpenses();
                updateSharedUsersList();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        databaseRef.addValueEventListener(tasksListener);
    }

    private void updateSharedUsersList() {
        databaseRef.child("lists").child(currentListId).child("users")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long userCount = snapshot.getChildrenCount();
                        tvSharedWith.setText("Lista compartida con " + userCount +
                                " usuario(s) • ID: " + currentListId.substring(0, 8) + "...");
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void shareListId() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Compartir Lista TATILIST");
        shareIntent.putExtra(Intent.EXTRA_TEXT,
                "Únete a mi lista TATILIST!\n\nID: " + currentListId + "\n\nUsa este ID en la app.");
        startActivity(Intent.createChooser(shareIntent, "Compartir lista"));
    }

    private void showJoinListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_join_list, null);
        EditText etListId = dialogView.findViewById(R.id.etListId);
        builder.setView(dialogView)
                .setTitle("Unirse a Lista TATILIST")
                .setPositiveButton("Unirse", (dialog, which) -> {
                    String newListId = etListId.getText().toString().trim();
                    if (!newListId.isEmpty()) {
                        joinList(newListId);
                    } else {
                        Toast.makeText(this, "Ingresa un ID válido", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void joinList(String newListId) {
        databaseRef.child("lists").child(newListId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            currentListId = newListId;
                            prefs.edit().putString("listId", currentListId).apply();
                            String userId = UUID.randomUUID().toString();
                            databaseRef.child("lists").child(currentListId)
                                    .child("users").child(userId).setValue(true);
                            Toast.makeText(MainActivity.this,
                                    "Te has unido a la lista correctamente", Toast.LENGTH_SHORT).show();
                            recreate();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "La lista no existe", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(MainActivity.this, "Error al unirse", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupTabLayout() {
        tabLayout.addTab(tabLayout.newTab().setText("Todas"));
        tabLayout.addTab(tabLayout.newTab().setText("Hoy"));
        tabLayout.addTab(tabLayout.newTab().setText("Completadas"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                filterTasks(tab.getPosition());
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private boolean isSameDay(long date1, long date2) {
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        c1.setTimeInMillis(date1);
        c2.setTimeInMillis(date2);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH) &&
                c1.get(Calendar.DAY_OF_MONTH) == c2.get(Calendar.DAY_OF_MONTH);
    }

    private void filterTasks(int position) {
        databaseRef.child("lists").child(currentListId).child("tasks")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        allTasks.clear();
                        long today = System.currentTimeMillis();
                        for (DataSnapshot taskSnapshot : snapshot.getChildren()) {
                            Task task = taskSnapshot.getValue(Task.class);
                            if (task != null) {
                                task.setFirebaseKey(taskSnapshot.getKey());
                                boolean addTask = false;
                                switch (position) {
                                    case 0:
                                        addTask = true;
                                        break;
                                    case 1:
                                        if (task.getDueDate() != null && !task.isCompleted()) {
                                            if (isSameDay(task.getDueDate(), today)) {
                                                addTask = true;
                                            }
                                        }
                                        break;
                                    case 2:
                                        if (task.isCompleted()) {
                                            addTask = true;
                                        }
                                        break;
                                }
                                if (addTask) {
                                    allTasks.add(task);
                                }
                            }
                        }
                        taskAdapter.notifyDataSetChanged();
                        updateTotalExpenses();
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        EditText etTitle = dialogView.findViewById(R.id.etTitle);
        EditText etDescription = dialogView.findViewById(R.id.etDescription);
        EditText etExpense = dialogView.findViewById(R.id.etExpense);
        Spinner spinnerPriority = dialogView.findViewById(R.id.spinnerPriority);
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinnerCategory);
        Button btnSelectDate = dialogView.findViewById(R.id.btnSelectDate);
        CheckBox cbRecurring = dialogView.findViewById(R.id.cbRecurring);
        RecyclerView rvSubtasks = dialogView.findViewById(R.id.rvSubtasks);
        Button btnAddSubtask = dialogView.findViewById(R.id.btnAddSubtask);

        List<Subtask> subtasks = new ArrayList<>();
        SubtaskAdapter subtaskAdapter = new SubtaskAdapter(subtasks);
        rvSubtasks.setLayoutManager(new LinearLayoutManager(this));
        rvSubtasks.setAdapter(subtaskAdapter);

        btnAddSubtask.setOnClickListener(v -> {
            subtasks.add(new Subtask("", 0.0, false));
            subtaskAdapter.notifyItemInserted(subtasks.size() - 1);
        });

        ArrayAdapter<CharSequence> priorityAdapter = ArrayAdapter.createFromResource(this,
                R.array.priority_array, android.R.layout.simple_spinner_item);
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPriority.setAdapter(priorityAdapter);

        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(this,
                R.array.category_array, android.R.layout.simple_spinner_item);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);

        final Long[] selectedDate = {null};
        btnSelectDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, day) -> {
                calendar.set(year, month, day);
                selectedDate[0] = calendar.getTimeInMillis();
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                btnSelectDate.setText(sdf.format(new Date(selectedDate[0])));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        builder.setView(dialogView)
                .setTitle("Nueva Tarea")
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String title = etTitle.getText().toString();
                    if (title.isEmpty()) {
                        Toast.makeText(this, "El título es obligatorio", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String description = etDescription.getText().toString();
                    String expenseStr = etExpense.getText().toString();
                    double expense = expenseStr.isEmpty() ? 0 : Double.parseDouble(expenseStr);
                    int priority = spinnerPriority.getSelectedItemPosition() + 1;
                    String category = spinnerCategory.getSelectedItem().toString();
                    boolean isRecurring = cbRecurring.isChecked();

                    Task task = new Task(title, description, selectedDate[0], priority,
                            false, expense, category, isRecurring, subtasks);

                    String taskId = databaseRef.child("lists").child(currentListId)
                            .child("tasks").push().getKey();
                    if (taskId != null) {
                        databaseRef.child("lists").child(currentListId)
                                .child("tasks").child(taskId).setValue(task);
                        String userId = UUID.randomUUID().toString();
                        databaseRef.child("lists").child(currentListId)
                                .child("users").child(userId).setValue(true);
                        showNotification("✅ Nueva tarea creada", title);
                        if (selectedDate[0] != null) {
                            requestCalendarPermissionAndAddEvent(task);
                        }
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void requestCalendarPermissionAndAddEvent(Task task) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_CALENDAR}, CALENDAR_PERMISSION_CODE);
        } else {
            addEventToCalendar(task);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALENDAR_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // No tenemos la tarea específica aquí, así que no agregamos evento
                // (opcional: podrías guardar en SharedPreferences para procesar después)
            }
        }
    }

    private void addEventToCalendar(Task task) {
        if (task.getDueDate() == null) return;
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DTSTART, task.getDueDate());
        values.put(CalendarContract.Events.DTEND, task.getDueDate() + 3600000);
        values.put(CalendarContract.Events.TITLE, task.getTitle());
        values.put(CalendarContract.Events.DESCRIPTION, task.getDescription());
        values.put(CalendarContract.Events.CALENDAR_ID, 1);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
        Uri uri = getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
        if (uri != null) {
            Toast.makeText(this, "📅 Añadido al calendario", Toast.LENGTH_SHORT).show();
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public void updateTask(Task task) {
        if (task.getFirebaseKey() != null) {
            databaseRef.child("lists").child(currentListId)
                    .child("tasks").child(task.getFirebaseKey()).setValue(task);
            if (task.isCompleted()) {
                showNotification("✔️ Tarea completada", task.getTitle());
            }
        }
    }

    public void deleteTask(Task task) {
        if (task.getFirebaseKey() != null) {
            databaseRef.child("lists").child(currentListId)
                    .child("tasks").child(task.getFirebaseKey()).removeValue();
        }
    }

    public void showEditDialog(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        EditText etTitle = dialogView.findViewById(R.id.etTitle);
        EditText etDescription = dialogView.findViewById(R.id.etDescription);
        EditText etExpense = dialogView.findViewById(R.id.etExpense);
        Spinner spinnerPriority = dialogView.findViewById(R.id.spinnerPriority);
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinnerCategory);
        Button btnSelectDate = dialogView.findViewById(R.id.btnSelectDate);
        CheckBox cbRecurring = dialogView.findViewById(R.id.cbRecurring);
        RecyclerView rvSubtasks = dialogView.findViewById(R.id.rvSubtasks);
        Button btnAddSubtask = dialogView.findViewById(R.id.btnAddSubtask);

        etTitle.setText(task.getTitle());
        etDescription.setText(task.getDescription());
        if (task.getExpense() > 0) {
            etExpense.setText(String.valueOf(task.getExpense()));
        }

        List<Subtask> subtasks = new ArrayList<>(task.getSubtasks());
        SubtaskAdapter subtaskAdapter = new SubtaskAdapter(subtasks);
        rvSubtasks.setLayoutManager(new LinearLayoutManager(this));
        rvSubtasks.setAdapter(subtaskAdapter);

        btnAddSubtask.setOnClickListener(v -> {
            subtasks.add(new Subtask("", 0.0, false));
            subtaskAdapter.notifyItemInserted(subtasks.size() - 1);
        });

        ArrayAdapter<CharSequence> priorityAdapter = ArrayAdapter.createFromResource(this,
                R.array.priority_array, android.R.layout.simple_spinner_item);
        priorityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPriority.setAdapter(priorityAdapter);
        spinnerPriority.setSelection(task.getPriority() - 1);

        ArrayAdapter<CharSequence> categoryAdapter = ArrayAdapter.createFromResource(this,
                R.array.category_array, android.R.layout.simple_spinner_item);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);
        String[] categories = getResources().getStringArray(R.array.category_array);
        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equals(task.getCategory())) {
                spinnerCategory.setSelection(i);
                break;
            }
        }

        cbRecurring.setChecked(task.isRecurring());
        final Long[] selectedDate = {task.getDueDate()};
        if (selectedDate[0] != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            btnSelectDate.setText(sdf.format(new Date(selectedDate[0])));
        }
        btnSelectDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            if (selectedDate[0] != null) {
                calendar.setTimeInMillis(selectedDate[0]);
            }
            new DatePickerDialog(this, (view, year, month, day) -> {
                calendar.set(year, month, day);
                selectedDate[0] = calendar.getTimeInMillis();
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                btnSelectDate.setText(sdf.format(new Date(selectedDate[0])));
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        builder.setView(dialogView)
                .setTitle("Editar Tarea")
                .setPositiveButton("Guardar", (dialog, which) -> {
                    task.setTitle(etTitle.getText().toString());
                    task.setDescription(etDescription.getText().toString());
                    String expenseStr = etExpense.getText().toString();
                    task.setExpense(expenseStr.isEmpty() ? 0 : Double.parseDouble(expenseStr));
                    task.setPriority(spinnerPriority.getSelectedItemPosition() + 1);
                    task.setCategory(spinnerCategory.getSelectedItem().toString());
                    task.setDueDate(selectedDate[0]);
                    task.setRecurring(cbRecurring.isChecked());
                    task.setSubtasks(subtasks);
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    updateTask(task);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void updateTotalExpenses() {
        Calendar calendar = Calendar.getInstance();
        int currentMonth = calendar.get(Calendar.MONTH);
        int currentYear = calendar.get(Calendar.YEAR);
        double total = 0;
        for (Task task : allTasks) {
            if (task.getExpense() > 0 && task.getDueDate() != null) {
                calendar.setTimeInMillis(task.getDueDate());
                if (calendar.get(Calendar.MONTH) == currentMonth &&
                        calendar.get(Calendar.YEAR) == currentYear) {
                    total += task.getExpense();
                }
            }
            // Sumar gastos de subtareas
            for (Subtask st : task.getSubtasks()) {
                if (st.getExpense() > 0 && task.getDueDate() != null) {
                    calendar.setTimeInMillis(task.getDueDate());
                    if (calendar.get(Calendar.MONTH) == currentMonth &&
                            calendar.get(Calendar.YEAR) == currentYear) {
                        total += st.getExpense();
                    }
                }
            }
        }
        tvTotalExpenses.setText(String.format(Locale.getDefault(),
                "Gastos del mes: $%.2f", total));
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void showNotification(String title, String content) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        NotificationManagerCompat.from(this).notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "TATILIST Notifications";
            String description = "Notificaciones de tareas";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tasksListener != null) {
            databaseRef.removeEventListener(tasksListener);
        }
    }
}

// --- Subtask Class ---
class Subtask {
    private String title;
    private double expense;
    private boolean completed;

    public Subtask() {}

    public Subtask(String title, double expense, boolean completed) {
        this.title = title;
        this.expense = expense;
        this.completed = completed;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public double getExpense() { return expense; }
    public void setExpense(double expense) { this.expense = expense; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}

// --- Subtask Adapter ---
class SubtaskAdapter extends RecyclerView.Adapter<SubtaskAdapter.ViewHolder> {
    private List<Subtask> subtasks;

    public SubtaskAdapter(List<Subtask> subtasks) {
        this.subtasks = subtasks;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subtask, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Subtask st = subtasks.get(position);
        holder.etTitle.setText(st.getTitle());
        holder.etExpense.setText(st.getExpense() > 0 ? String.valueOf(st.getExpense()) : "");
        holder.cbCompleted.setChecked(st.isCompleted());

        holder.etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                st.setTitle(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });






        holder.etExpense.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    st.setExpense(s.length() == 0 ? 0 : Double.parseDouble(s.toString()));
                } catch (NumberFormatException ignored) {}
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        holder.cbCompleted.setOnCheckedChangeListener((button, isChecked) -> st.setCompleted(isChecked));
    }

    @Override
    public int getItemCount() {
        return subtasks.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        EditText etTitle, etExpense;
        CheckBox cbCompleted;

        ViewHolder(View v) {
            super(v);
            etTitle = v.findViewById(R.id.etSubtaskTitle);
            etExpense = v.findViewById(R.id.etSubtaskExpense);
            cbCompleted = v.findViewById(R.id.cbSubtaskCompleted);
        }
    }
}

// --- Task Class (actualizada) ---
class Task {
    private String title;
    private String description;
    private Long dueDate;
    private int priority;
    private boolean completed;
    private double expense;
    private String category;
    private boolean isRecurring;
    private String firebaseKey;
    private List<Subtask> subtasks;

    public Task() {}

    public Task(String title, String description, Long dueDate, int priority,
                boolean completed, double expense, String category, boolean isRecurring,
                List<Subtask> subtasks) {
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.priority = priority;
        this.completed = completed;
        this.expense = expense;
        this.category = category;
        this.isRecurring = isRecurring;
        this.subtasks = subtasks != null ? subtasks : new ArrayList<>();
    }

    // Getters y Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getDueDate() { return dueDate; }
    public void setDueDate(Long dueDate) { this.dueDate = dueDate; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public double getExpense() { return expense; }
    public void setExpense(double expense) { this.expense = expense; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isRecurring() { return isRecurring; }
    public void setRecurring(boolean recurring) { isRecurring = recurring; }
    public String getFirebaseKey() { return firebaseKey; }
    public void setFirebaseKey(String key) { this.firebaseKey = key; }
    public List<Subtask> getSubtasks() { return subtasks; }
    public void setSubtasks(List<Subtask> subtasks) { this.subtasks = subtasks; }
}

// --- TaskAdapter (actualizado para mostrar subtareas) ---
class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private List<Task> tasks;
    private MainActivity activity;

    public TaskAdapter(List<Task> tasks, MainActivity activity) {
        this.tasks = tasks;
        this.activity = activity;
    }

    @Override
    public TaskViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @Override
    public void onBindViewHolder(TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.tvTitle.setText(task.getTitle());
        holder.tvDescription.setText(task.getDescription());
        holder.cbCompleted.setChecked(task.isCompleted());

        double totalExpense = task.getExpense();
        for (Subtask st : task.getSubtasks()) {
            totalExpense += st.getExpense();
        }

        if (totalExpense > 0) {
            holder.tvExpense.setVisibility(View.VISIBLE);
            holder.tvExpense.setText(String.format(Locale.getDefault(), "$%.2f", totalExpense));
        } else {
            holder.tvExpense.setVisibility(View.GONE);
        }

        holder.tvCategory.setText(task.getCategory());
        if (task.getDueDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            holder.tvDueDate.setText(sdf.format(new Date(task.getDueDate())));
            holder.tvDueDate.setVisibility(View.VISIBLE);
        } else {
            holder.tvDueDate.setVisibility(View.GONE);
        }

        int priorityColor;
        switch (task.getPriority()) {
            case 1: priorityColor = 0xFFFF5252; break;
            case 2: priorityColor = 0xFFFFAB40; break;
            case 3: priorityColor = 0xFF69F0AE; break;
            default: priorityColor = 0xFF90A4AE; break;
        }
        holder.viewPriority.setBackgroundColor(priorityColor);

        if (task.isRecurring()) {
            holder.ivRecurring.setVisibility(View.VISIBLE);
        } else {
            holder.ivRecurring.setVisibility(View.GONE);
        }

        holder.cbCompleted.setOnClickListener(v -> {
            task.setCompleted(holder.cbCompleted.isChecked());
            activity.updateTask(task);
        });

        holder.itemView.setOnClickListener(v -> activity.showEditDialog(task));

        holder.itemView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(activity)
                    .setTitle("Eliminar tarea")
                    .setMessage("¿Estás seguro de eliminar esta tarea?")
                    .setPositiveButton("Eliminar", (dialog, which) -> activity.deleteTask(task))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbCompleted;
        TextView tvTitle, tvDescription, tvExpense, tvCategory, tvDueDate;
        View viewPriority;
        ImageView ivRecurring;

        public TaskViewHolder(View itemView) {
            super(itemView);
            cbCompleted = itemView.findViewById(R.id.cbCompleted);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvExpense = itemView.findViewById(R.id.tvExpense);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvDueDate = itemView.findViewById(R.id.tvDueDate);
            viewPriority = itemView.findViewById(R.id.viewPriority);
            ivRecurring = itemView.findViewById(R.id.ivRecurring);
        }
    }
}