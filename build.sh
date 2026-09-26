#!/bin/sh
# Construit MineGen.jar (plugin Paper) et les classes de banc de test.
set -e
LIB="${MINEGEN_LIB:-$HOME/.minegen/lib}"      # paper-api.jar + jars adventure
OUT=java/out
JAR=MineGen.jar

echo "== tables vanilla =="
python export_splines.py
python export_biomes.py
python export_surface.py

echo "== banc de test (sans dependance Paper) =="
rm -rf "$OUT" && mkdir -p "$OUT"
javac -d "$OUT" java/src/mnw/*.java

echo "== plugin =="
# javac est le binaire Windows : il ignore les chemins POSIX de Git Bash.
CP=$(ls "$LIB"/*.jar 2>/dev/null | while read -r j; do
       if command -v cygpath >/dev/null 2>&1; then cygpath -w "$j"; else printf "%s
" "$j"; fi
     done | paste -sd';' -)
if [ -z "$CP" ]; then
  echo "  (jars Paper absents de $LIB : plugin non construit)"
  exit 0
fi
rm -rf java/plugin_out java/jar && mkdir -p java/plugin_out java/jar
javac -cp "$CP" -d java/plugin_out java/src/mnw/*.java java/src/mnw/plugin/*.java

# La classe d'ecriture directe est la seule a dependre de NMS : elle se compile
# contre le jar serveur, pas contre paper-api, et n'est chargee qu'a l'execution.
SRV="${MINEGEN_SERVER_JAR:-server-mbp/versions/1.21.11/myboxpaper-1.21.11.jar}"
if [ -f "$SRV" ]; then
  rm -rf java/nms_out && mkdir -p java/nms_out
  SRVW=$(cygpath -w "$SRV" 2>/dev/null || printf '%s' "$SRV")
  OUTW=$(cygpath -w java/plugin_out 2>/dev/null || printf '%s' java/plugin_out)
  # datafixerupper fournit MapCodec, present dans la signature de BiomeSource
  DFU=$(find "$(dirname "$SRV")/../../libraries" -name "datafixerupper-*.jar" 2>/dev/null | head -1)
  [ -n "$DFU" ] && DFUW=";$(cygpath -w "$DFU" 2>/dev/null || printf '%s' "$DFU")" || DFUW=""
  javac -cp "$CP;$SRVW;$OUTW$DFUW" -d java/nms_out java/src_nms/mnw/plugin/nms/*.java
  echo "  ecriture directe NMS compilee"
else
  echo "  (jar serveur absent : MineGen retombera sur ChunkData.setRegion)"
fi

cp -r java/plugin_out/mnw java/jar/
[ -d java/nms_out/mnw ] && cp -r java/nms_out/mnw java/jar/
rm -f java/jar/mnw/{Render,Teleport,ParallelTP,Profile,Stats,Dbg,VanillaPreview,PluginCheck,Perlin,VanillaBaseline}*.class
mkdir -p java/jar/mnw/res && cp java/res/*.bin java/jar/mnw/res/
cp java/res_pkg/plugin.yml java/jar/
# Depuis 1.20.5 Paper remappe les plugins qui touchent a NMS, en supposant des
# mappings Spigot. Le notre est compile contre le serveur mojang-mappe : sans cet
# attribut, Paper le remapperait a tort et la classe ne se lierait pas.
printf 'paperweight-mappings-namespace: mojang
' > java/jar/manifest.txt
(cd java/jar && jar --create --file "../../$JAR" --manifest manifest.txt    $(ls | grep -v '^manifest.txt$'))
echo "== $JAR : $(du -h $JAR | cut -f1) =="
