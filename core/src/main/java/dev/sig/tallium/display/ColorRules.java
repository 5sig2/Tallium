package dev.sig.tallium.display;

import java.util.*;

public final class ColorRules {
    public record Row(int lower, String hex) {}
    public boolean enabled = true;
    public List<Row> rows = new ArrayList<>();
    public void validate() {
        if (rows == null || rows.isEmpty() || rows.size() > 32) throw new IllegalArgumentException("Colors need 1–32 rows.");
        Set<Integer> bounds = new HashSet<>();
        for (Row row:rows) if (row == null || row.lower()<0 || !bounds.add(row.lower()) || row.hex()==null || !row.hex().matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Use unique nonnegative thresholds and #RRGGBB colors.");
        if (!bounds.contains(0)) throw new IllegalArgumentException("Colors need a base row at 0.");
        rows.sort(Comparator.comparingInt(Row::lower));
    }
    public int color(long value) {
        if (!enabled) return 0xFFFFFFFF;
        Row result = rows.stream().filter(row->row.lower()==0).findFirst().orElse(rows.getFirst());
        for (Row row:rows) if (row.lower()<=value&&row.lower()>=result.lower()) result=row;
        return 0xFF000000 | Integer.parseInt(result.hex().substring(1),16);
    }
    public static ColorRules used(String family) {
        int step = switch (family) { case "food" -> 4; case "potions", "totems" -> 1; default -> 16; };
        ColorRules r=new ColorRules(); String[] colors={"#55FF55","#00AA00","#FFFF55","#FFAA00","#FF5555"};
        for(int n=0;n<5;n++) r.rows.add(new Row(n*step,colors[n])); return r;
    }
    public static ColorRules remaining(String family) {
        int yellow=switch(family){case "food","arrows","cobwebs"->8; case "potions","totems"->2;default->4;};
        int green=switch(family){case "food","arrows","cobwebs"->16;case "potions","totems"->3;default->8;};
        ColorRules r=new ColorRules();r.rows.addAll(List.of(new Row(0,"#FF5555"),new Row(1,"#FFAA00"),new Row(yellow,"#FFFF55"),new Row(green,"#55FF55"))); return r;
    }
}
