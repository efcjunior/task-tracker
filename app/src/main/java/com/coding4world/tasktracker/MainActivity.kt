    package com.coding4world.tasktracker

    import android.app.TimePickerDialog
    import android.content.Context
    import android.os.Bundle
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.setContent
    import androidx.compose.animation.AnimatedVisibility
    import androidx.compose.foundation.ExperimentalFoundationApi
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.relocation.BringIntoViewRequester
    import androidx.compose.foundation.relocation.bringIntoViewRequester
    import androidx.compose.foundation.rememberScrollState
    import androidx.compose.foundation.verticalScroll
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.Add
    import androidx.compose.material.icons.filled.ArrowDropDown
    import androidx.compose.material.icons.filled.Close
    import androidx.compose.material.icons.filled.Delete
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.focus.FocusRequester
    import androidx.compose.ui.focus.focusRequester
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.toArgb
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.tooling.preview.Preview
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.lifecycle.ViewModel
    import androidx.lifecycle.ViewModelProvider
    import androidx.lifecycle.viewModelScope
    import androidx.lifecycle.viewmodel.compose.viewModel
    import androidx.room.*
    import com.coding4world.tasktracker.ui.theme.TaskTrackerAppTheme

    import kotlinx.coroutines.flow.Flow
    import kotlinx.coroutines.launch
    import java.text.SimpleDateFormat
    import java.util.*

    class MainActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent {
                TaskTrackerAppTheme {
                    TaskTrackerScreen()
                }
            }
        }
    }

    enum class TaskStatus {
        PENDING,
        COMPLETED
    }

    @Entity(tableName = "tasks")
    @TypeConverters(DateConverter::class)
    data class Task(
        @PrimaryKey(autoGenerate = true) val id: Int = 0,
        var description: String,
        var whenDateTime: Date,
        var status: TaskStatus
    )

    class DateConverter {
        @TypeConverter
        fun fromTimestamp(value: Long?): Date? {
            return value?.let { Date(it) }
        }

        @TypeConverter
        fun dateToTimestamp(date: Date?): Long? {
            return date?.time?.toLong()
        }
    }

    @Dao
    interface TaskDao {
        @Query("SELECT * FROM tasks ORDER BY whenDateTime ASC")
        fun getAllTasks(): Flow<List<Task>>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertTask(task: Task)

        @Update
        suspend fun updateTask(task: Task)

        @Delete
        suspend fun deleteTask(task: Task)
    }

    @Database(entities = [Task::class], version = 1, exportSchema = false)
    @TypeConverters(DateConverter::class)
    abstract class TaskDatabase : RoomDatabase() {
        abstract fun taskDao(): TaskDao

        companion object {
            @Volatile
            private var INSTANCE: TaskDatabase? = null

            fun getDatabase(context: Context): TaskDatabase {
                return INSTANCE ?: synchronized(this) {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        TaskDatabase::class.java,
                        "task_database"
                    ).build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }

    class TaskViewModelFactory(context: Context) : ViewModelProvider.Factory {
        private val taskDao = TaskDatabase.getDatabase(context).taskDao()

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TaskViewModel(taskDao) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    class TaskViewModel(private val taskDao: TaskDao) : ViewModel() {
        val allTasks: Flow<List<Task>> = taskDao.getAllTasks()

        fun addTask(task: Task) {
            viewModelScope.launch {
                taskDao.insertTask(task)
            }
        }

        fun updateTask(task: Task) {
            viewModelScope.launch {
                taskDao.updateTask(task)
            }
        }

        fun deleteTask(task: Task) {
            viewModelScope.launch {
                taskDao.deleteTask(task)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun TaskTrackerScreen(
        taskViewModel: TaskViewModel = viewModel(factory = TaskViewModelFactory(LocalContext.current)),
    ) {
        var taskDescription by remember { mutableStateOf("") }
        var selectedDate by remember { mutableStateOf<Date?>(null) }
        var selectedTime by remember { mutableStateOf<Date?>(null) }
        var isEditing by remember { mutableStateOf(false) }
        var editingTask: Task? by remember { mutableStateOf(null) }
        val tasks by taskViewModel.allTasks.collectAsState(initial = emptyList())
        val scrollState = rememberScrollState()
        val focusRequester = remember { FocusRequester() }
        val bringIntoViewRequester = remember { BringIntoViewRequester() }
        var showCompletedTasks by remember { mutableStateOf(false) }
        var isFormVisible by remember { mutableStateOf(false) }

        // Variáveis para exibir DatePicker e TimePicker
        var showDatePickerDialog by remember { mutableStateOf(false) }
        var showTimePickerDialog by remember { mutableStateOf(false) }

        // Filtrar e agrupar as tarefas não concluídas
        val groupedTasks = tasks.filter { it.status != TaskStatus.COMPLETED }
            .groupBy {
                it.whenDateTime.takeIf { date -> date != Date(Long.MAX_VALUE) }?.let { date ->
                    SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(date)
                } ?: stringResource(R.string.task_without_date)
            }

        // Filtrar as tarefas concluídas
        val completedTasks = tasks.filter { it.status == TaskStatus.COMPLETED }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    )
                )
            },
            floatingActionButtonPosition = FabPosition.End,
            floatingActionButton = {
                if (!isFormVisible) {
                    FloatingActionButton(
                        onClick = {
                            isFormVisible = true
                            taskDescription = ""
                            selectedDate = null
                            selectedTime = null
                            isEditing = false
                            editingTask = null
                        },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.add_task_fab),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            },
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .verticalScroll(scrollState)
                            .padding(16.dp)
                    ) {
                        groupedTasks.forEach { (date, tasks) ->
                            Text(
                                text = date,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            tasks.forEach { task ->
                                TaskCard(task, taskViewModel)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (completedTasks.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCompletedTasks = !showCompletedTasks }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.completed_tasks_section),
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (showCompletedTasks) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropDown,
                                    contentDescription = stringResource(R.string.expand_collapse_completed_tasks),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            AnimatedVisibility(visible = showCompletedTasks) {
                                Column {
                                    completedTasks.forEach { task ->
                                        TaskCard(task, taskViewModel)
                                    }
                                }
                            }
                        }
                    }

                    if (isFormVisible) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = taskDescription,
                                onValueChange = { taskDescription = it },
                                label = { Text(stringResource(R.string.task_description_label)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .bringIntoViewRequester(bringIntoViewRequester),
                                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePickerDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        selectedDate?.let {
                                            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
                                        } ?: stringResource(R.string.select_date_button)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp)) // Espaço entre os botões

                                OutlinedButton(
                                    onClick = { showTimePickerDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        selectedTime?.let {
                                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                                        } ?: stringResource(R.string.select_time_button)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Button(
                                    onClick = {
                                        isFormVisible = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel_task_fab))
                                }

                                Button(
                                    onClick = {
                                        if (taskDescription.isNotEmpty()) {
                                            val calendar = Calendar.getInstance()
                                            selectedDate?.let { calendar.time = it }
                                            selectedTime?.let {
                                                val timeCalendar = Calendar.getInstance().apply {
                                                    time = it
                                                }
                                                calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY))
                                                calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE))
                                            }

                                            val newTask = Task(
                                                description = taskDescription,
                                                whenDateTime = if (selectedDate != null || selectedTime != null) calendar.time else Date(Long.MAX_VALUE),
                                                status = TaskStatus.PENDING
                                            )
                                            if (isEditing) {
                                                editingTask?.let {
                                                    taskViewModel.updateTask(newTask.copy(id = it.id))
                                                }
                                                isEditing = false
                                                editingTask = null
                                            } else {
                                                taskViewModel.addTask(newTask)
                                            }
                                            taskDescription = ""
                                            selectedDate = null
                                            selectedTime = null
                                            isFormVisible = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(text = if (isEditing) stringResource(R.string.update_task_button) else stringResource(R.string.add_task_button), color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        )

        ShowDatePickerDialog(
            showDatePickerDialog = showDatePickerDialog,
            onDateSelected = { date ->
                selectedDate = date
                showDatePickerDialog = false
            },
            onDismiss = {
                showDatePickerDialog = false
            }
        )

        ShowTimePickerDialog(
            showTimePickerDialog = showTimePickerDialog,
            onTimeSelected = { time ->
                selectedTime = time
                showTimePickerDialog = false
            },
            onDismiss = {
                showTimePickerDialog = false
            }
        )

        // Solicitar foco e rolar para o campo de descrição somente após a composição
        LaunchedEffect(isFormVisible) {
            if (isFormVisible) {
                bringIntoViewRequester.bringIntoView()
                focusRequester.requestFocus()
            }
        }
    }

    @Composable
    fun ShowDatePickerDialog(
        showDatePickerDialog: Boolean,
        onDateSelected: (Date?) -> Unit,
        onDismiss: () -> Unit
    ) {
        if (showDatePickerDialog) {
            val context = LocalContext.current
            val calendar = Calendar.getInstance()
            val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
            val secondaryColor = MaterialTheme.colorScheme.secondary.toArgb()

            val datePickerDialog = android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    onDateSelected(calendar.time)
                    onDismiss()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            // Aplicar cores do tema aos botões "OK" e "Cancelar"
            datePickerDialog.setOnShowListener {
                datePickerDialog.getButton(android.app.DatePickerDialog.BUTTON_POSITIVE)
                    .setTextColor(primaryColor)
                datePickerDialog.getButton(android.app.DatePickerDialog.BUTTON_NEGATIVE)
                    .setTextColor(secondaryColor)
            }

            datePickerDialog.show()
        }
    }

    @Composable
    fun ShowTimePickerDialog(
        showTimePickerDialog: Boolean,
        onTimeSelected: (Date?) -> Unit,
        onDismiss: () -> Unit
    ) {
        if (showTimePickerDialog) {
            val context = LocalContext.current
            val calendar = Calendar.getInstance()
            val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
            val secondaryColor = MaterialTheme.colorScheme.secondary.toArgb()

            val timePickerDialog = TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)
                    onTimeSelected(calendar.time)
                    onDismiss()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            )

            // Aplicar cores do tema aos botões "OK" e "Cancelar"
            timePickerDialog.setOnShowListener {
                timePickerDialog.getButton(TimePickerDialog.BUTTON_POSITIVE)
                    .setTextColor(primaryColor)
                timePickerDialog.getButton(TimePickerDialog.BUTTON_NEGATIVE)
                    .setTextColor(secondaryColor)
            }

            timePickerDialog.show()
        }
    }


    @Composable
    fun TaskCard(task: Task, taskViewModel: TaskViewModel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clickable {
                    // Lógica para editar a tarefa, se necessário
                },
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = task.status == TaskStatus.COMPLETED,
                    onClick = {
                        taskViewModel.updateTask(
                            task.copy(
                                status = if (task.status == TaskStatus.COMPLETED) TaskStatus.PENDING else TaskStatus.COMPLETED
                            )
                        )
                    },
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = task.description, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = if (task.whenDateTime == Date(Long.MAX_VALUE)) "" else SimpleDateFormat(
                            "HH:mm",
                            Locale.getDefault()
                        ).format(task.whenDateTime),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = { taskViewModel.deleteTask(task) },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }


    @Preview(showBackground = true)
    @Composable
    fun TaskTrackerScreenPreview() {
        TaskTrackerAppTheme {
            TaskTrackerScreen()
        }
    }
