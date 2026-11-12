"""native-reflect.py JAR... OUT: the GraalVM native-image settings for fsh's and parley's own part
of the image, made from the jars as the image is built, so a class or file that is renamed, added
or removed never leaves a stale name behind:

  OUT (reflect-config.json)        every class of fsh and parley in the jars, with all their
                                   members: builtins are made by their constructors and read
                                   their options' enums (valueOf) by reflection, which a recording
                                   of what ran may miss
  resource-config.json beside OUT  every file in those jars that isn't a class (settings, the
                                   ServiceLoader lists in META-INF/services, version.properties,
                                   icons), and the properties files BaseObject looks for beside
                                   each class and in META-INF/parley, whether they are there or not

What the JDK and the other libraries need is recorded with the tracing agent instead
(tools/record-native-config.sh), into
src/main/resources/META-INF/native-image/us.bringardner/fsh/reachability-metadata.json.

native-reflect.py --strip FILE JAR...: take fsh's and parley's part out of that recorded FILE (it is
made here, from the jars): their classes, except that one the keyboard library calls stays marked
jniAccessible, and the files in the jars."""
import json, os, re, sys, zipfile


def own_files(jars):
    """(classes, other files) of fsh and parley in the jars"""
    classes, files = [], set()
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if name.endswith("/") or "module-info" in name:
                    continue
                if name.endswith(".class"):
                    if name.startswith("us/bringardner/"):
                        classes.append(name[:-6].replace("/", "."))
                elif name != "META-INF/MANIFEST.MF" and not name.startswith(("META-INF/maven/", "META-INF/native-image/")) \
                        and not re.match(r"META-INF/[^/]*\.(SF|DSA|RSA|EC)$", name):
                    files.add(name)
    return classes, files

if sys.argv[1] == "--strip":
    path = sys.argv[2]
    files = own_files(sys.argv[3:])[1]
    with open(path) as f:
        data = json.load(f)
    kept = []
    for e in data.get("reflection", []):
        t = e.get("type")
        if isinstance(t, str) and t.startswith("us.bringardner."):
            if e.get("jniAccessible"):
                kept.append({"type": t, "jniAccessible": True})
        else:
            kept.append(e)
    data["reflection"] = kept
    data["resources"] = [e for e in data.get("resources", []) if not (e.get("module") is None and (
        e.get("glob", "").startswith(("us/bringardner/", "META-INF/parley/", "META-INF/services/us.bringardner."))
        or e.get("glob") in files))]
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print(len(data["reflection"]), "classes,", len(data["resources"]), "files kept")
    sys.exit(0)

out = sys.argv[-1]
classes, resources = own_files(sys.argv[1:-1])
entries = [{"name": name, "allDeclaredConstructors": True, "allDeclaredMethods": True, "allDeclaredFields": True,
            "allPublicConstructors": True, "allPublicMethods": True, "allPublicFields": True} for name in classes]
with open(out, "w") as f:
    json.dump(entries, f, indent=1)
patterns = [{"pattern": "\\Q" + name + "\\E"} for name in sorted(resources)]
patterns += [{"pattern": "us/bringardner/.*\\.properties"}, {"pattern": "META-INF/parley/.*\\.properties"}]
with open(os.path.join(os.path.dirname(out), "resource-config.json"), "w") as f:
    json.dump({"resources": {"includes": patterns}, "bundles": []}, f, indent=1)
print(len(entries), "classes,", len(resources), "files")
