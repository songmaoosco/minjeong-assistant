<?xml version="1.0" encoding="utf-8"?>

<androidx.drawerlayout.widget.DrawerLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/drawerLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#FFF9F5">

    <!-- 메인 화면 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:background="#FFF9F5">

        <!-- 상단바 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="72dp"
            android:gravity="center_vertical"
            android:paddingStart="18dp"
            android:paddingEnd="18dp">

            <Button
                android:id="@+id/menuButton"
                android:layout_width="52dp"
                android:layout_height="52dp"
                android:text="☰"
                android:textSize="24sp"
                android:textColor="#29231F"
                android:backgroundTint="#FFFFFF"
                android:elevation="2dp" />

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:gravity="center"
                android:text="민정 AI"
                android:textColor="#29231F"
                android:textSize="20sp"
                android:textStyle="bold" />

            <Button
                android:id="@+id/newChatButton"
                android:layout_width="52dp"
                android:layout_height="52dp"
                android:text="＋"
                android:textSize="25sp"
                android:textColor="#29231F"
                android:backgroundTint="#FFFFFF"
                android:elevation="2dp" />

        </LinearLayout>

        <!-- 채팅 영역 -->
        <ScrollView
            android:id="@+id/scrollView"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:fillViewport="true"
            android:paddingStart="18dp"
            android:paddingEnd="18dp"
            android:clipToPadding="false">

            <LinearLayout
                android:id="@+id/chatContainer"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:paddingTop="12dp"
                android:paddingBottom="20dp" />

        </ScrollView>

        <!-- 입력 영역 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:paddingStart="14dp"
            android:paddingEnd="14dp"
            android:paddingTop="8dp"
            android:paddingBottom="14dp">

            <EditText
                android:id="@+id/inputField"
                android:layout_width="0dp"
                android:layout_height="58dp"
                android:layout_weight="1"
                android:hint="민정이한테 말 걸어봐"
                android:textColor="#29231F"
                android:textColorHint="#9B918B"
                android:textSize="16sp"
                android:singleLine="true"
                android:paddingStart="22dp"
                android:paddingEnd="18dp"
                android:backgroundTint="#FFFFFF"
                android:elevation="3dp" />

            <Button
                android:id="@+id/sendButton"
                android:layout_width="58dp"
                android:layout_height="58dp"
                android:layout_marginStart="8dp"
                android:text="➤"
                android:textSize="22sp"
                android:textColor="#FFFFFF"
                android:backgroundTint="#FF8A3D"
                android:elevation="3dp" />

        </LinearLayout>

    </LinearLayout>

    <!-- 왼쪽 대화 목록 -->
    <LinearLayout
        android:layout_width="290dp"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        android:orientation="vertical"
        android:background="#FFFFFF"
        android:padding="18dp">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="대화 목록"
            android:textColor="#29231F"
            android:textSize="20sp"
            android:textStyle="bold"
            android:paddingTop="16dp"
            android:paddingBottom="18dp" />

        <ListView
            android:id="@+id/sessionListView"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:divider="@null"
            android:dividerHeight="8dp" />

    </LinearLayout>

</androidx.drawerlayout.widget.DrawerLayout>