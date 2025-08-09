package com.example.utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Pythonの辞書のような機能を提供するクラス
 * String型のキーと値のペアを管理
 */
public class Params {
    private final Map<String, String> params;
    
    public Params() {
        // LinkedHashMapを使用して挿入順を保持
        this.params = new LinkedHashMap<>();
    }
    
    /**
     * パラメータを追加
     * @param key キー
     * @param value 値
     * @return このParamsオブジェクト（チェーンメソッド用）
     */
    public Params put(String key, String value) {
        params.put(key, value);
        return this;
    }
    
    /**
     * int値を文字列として追加
     */
    public Params put(String key, int value) {
        return put(key, String.valueOf(value));
    }
    
    /**
     * double値を文字列として追加
     */
    public Params put(String key, double value) {
        return put(key, String.valueOf(value));
    }
    
    /**
     * boolean値を文字列として追加
     */
    public Params put(String key, boolean value) {
        return put(key, String.valueOf(value));
    }
    
    /**
     * 値を取得
     * @param key キー
     * @return 値（存在しない場合はnull）
     */
    public String get(String key) {
        return params.get(key);
    }
    
    /**
     * キーが存在するかチェック
     */
    public boolean containsKey(String key) {
        return params.containsKey(key);
    }
    
    /**
     * すべてのキーを取得
     */
    public Set<String> keySet() {
        return params.keySet();
    }
    
    /**
     * パラメータの数を取得
     */
    public int size() {
        return params.size();
    }
    
    /**
     * 空かどうかチェック
     */
    public boolean isEmpty() {
        return params.isEmpty();
    }
    
    /**
     * 内部のMapを取得（読み取り専用）
     */
    public Map<String, String> getParams() {
        return new LinkedHashMap<>(params);
    }
    
    /**
     * CSVヘッダー行を生成
     */
    public String toCsvHeader() {
        return String.join(",", params.keySet());
    }
    
    /**
     * CSV値行を生成
     */
    public String toCsvValues() {
        return String.join(",", params.values());
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Params{");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
}