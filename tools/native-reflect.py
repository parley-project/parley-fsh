"""native-reflect.py JAR... OUT: a reflect-config.json (GraalVM native image) for every class of
fsh and parley in the jars, with all their members: builtins are made by their constructors and
read their options' enums (valueOf) by reflection, which a recording of what ran may miss."""
import json, sys, zipfile

out = sys.argv[-1]
entries = []
for jar in sys.argv[1:-1]:
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.startswith("us/bringardner/") and name.endswith(".class") and "module-info" not in name:
                entries.append({"name": name[:-6].replace("/", "."), "allDeclaredConstructors": True,
                                "allDeclaredMethods": True, "allDeclaredFields": True,
                                "allPublicConstructors": True, "allPublicMethods": True, "allPublicFields": True})
with open(out, "w") as f:
    json.dump(entries, f, indent=1)
print(len(entries), "classes")
