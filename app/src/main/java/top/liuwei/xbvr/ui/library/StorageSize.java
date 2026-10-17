package top.liuwei.xbvr.ui.library;

import java.util.Locale;

/** SI byte units, matching the server web UI's three significant digits. */
final class StorageSize {
    static String format(Long bytes,Locale locale){
        if(bytes==null||bytes<0)return null;
        String[] units={"B","kB","MB","GB","TB","PB","EB"};
        java.math.BigDecimal value=java.math.BigDecimal.valueOf(bytes);
        java.math.BigDecimal thousand=java.math.BigDecimal.valueOf(1000);int unit=0;
        while(value.compareTo(thousand)>=0&&unit<units.length-1){value=value.divide(thousand);unit++;}
        value=value.round(new java.math.MathContext(3,java.math.RoundingMode.HALF_UP));
        java.text.NumberFormat number=java.text.NumberFormat.getNumberInstance(locale);
        number.setGroupingUsed(false);number.setMaximumFractionDigits(6);
        return number.format(value)+" "+units[unit];
    }
}
