package com.example.aiagent;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private Spinner spCharacter, spDifficulty, spEndAction;

    private EditText etChallengeCount, etFixedSeed;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 返回按钮
        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            saveData(); // 保存数据
            onBackPressed(); // 退出设置界面
        });

        // 初始化控件
        spCharacter = findViewById(R.id.spCharacter);
        spDifficulty = findViewById(R.id.spDifficulty);
        spEndAction = findViewById(R.id.spEndAction);
        etChallengeCount = findViewById(R.id.etChallengeCount);
        etFixedSeed = findViewById(R.id.fixedSeed);

        // 绑定角色数据
        ArrayAdapter<CharSequence> characterAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.characters_array, // 在 res/values/strings.xml 中定义
                android.R.layout.simple_spinner_item
        );
        characterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCharacter.setAdapter(characterAdapter);

        // 绑定难度数据
        ArrayAdapter<CharSequence> difficultyAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.difficulty_array, // 在 res/values/strings.xml 中定义
                android.R.layout.simple_spinner_item
        );
        difficultyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDifficulty.setAdapter(difficultyAdapter);

        // 绑定结束操作数据
        ArrayAdapter<CharSequence> endActionAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.end_action_array, // 在 res/values/strings.xml 中定义
                android.R.layout.simple_spinner_item
        );
        endActionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spEndAction.setAdapter(endActionAdapter);

        // 初始化 SharedPreferences
        sharedPreferences = getSharedPreferences("GameSettings", Context.MODE_PRIVATE);

        // 加载已保存的数据
        loadSavedData();
    }

    private void loadSavedData() {
        // 加载挑战次数
        int challengeCount = sharedPreferences.getInt("challengeCount", 1);
        etChallengeCount.setText(String.valueOf(challengeCount));

        // 加载种子
        String fixedSeed = sharedPreferences.getString("fixedSeed", "");
        etFixedSeed.setText(fixedSeed);

        // 加载角色
        String character = sharedPreferences.getString("character", "铁甲战士");
        setSpinnerSelection(spCharacter, character);

        // 加载难度
        String difficulty = sharedPreferences.getString("difficulty", "0");
        setSpinnerSelection(spDifficulty, difficulty);

        // 加载结束操作
        String endAction = sharedPreferences.getString("endAction", "退出游戏");
        setSpinnerSelection(spEndAction, endAction);
    }

    // 设置 Spinner 的选中项
    private void setSpinnerSelection(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equals(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void saveData() {
        // 获取用户输入
        String challengeCountStr = etChallengeCount.getText().toString();
        String fixedSeed = etFixedSeed.getText().toString();
        String character = spCharacter.getSelectedItem().toString();
        String difficulty = spDifficulty.getSelectedItem().toString();
        String endAction = spEndAction.getSelectedItem().toString();

        // 验证输入
        if (challengeCountStr.isEmpty()) {
            Toast.makeText(this, "请输入挑战次数", Toast.LENGTH_SHORT).show();
            return;
        }

        // 将挑战次数转换为整数
        int challengeCount = Integer.parseInt(challengeCountStr);

        // 保存数据到 SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("challengeCount", challengeCount);
        editor.putString("fixedSeed", fixedSeed);
        editor.putString("character", character);
        editor.putString("difficulty", difficulty);
        editor.putString("endAction", endAction);
        editor.apply();

        // 提示用户
        Toast.makeText(this, "数据已保存", Toast.LENGTH_SHORT).show();
    }
}
