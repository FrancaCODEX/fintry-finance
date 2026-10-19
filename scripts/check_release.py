"""Keep the released version, visible documentation and changelog synchronized."""
from pathlib import Path
import re
import argparse
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
project = ET.parse(root / "backend/pom.xml").getroot()
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
version = project.findtext("m:version", namespaces=ns)
assert version and re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version), "Defina uma versão de entrega MAJOR.MINOR.PATCH no Maven."
changelog = (root / "docs/01-product/changelog.md").read_text()
releases = re.findall(r"^## \[([0-9]+\.[0-9]+\.[0-9]+)\] - (\d{4}-\d{2}-\d{2})$", changelog, re.M)
assert releases and releases[0][0] == version, "A versão mais recente do changelog deve corresponder ao Maven."
assert len({v for v, _ in releases}) == len(releases), "Não repita versões no changelog."
assert f"**Versão atual: {version}**" in (root / "README.md").read_text(), "Atualize a versão no README."
assert project.findtext("m:build/m:finalName", namespaces=ns) == "fintry", "Preserve o nome estável fintry.jar."
assert '@project.version@' in (root / "backend/src/main/resources/application.yml").read_text(), "A versão deve vir do Maven."
print(f"Versão {version}, README e changelog consistentes.")

parser = argparse.ArgumentParser()
parser.add_argument("--base", help="Commit base do PR; exige incremento da versão em relação a ele.")
args = parser.parse_args()
if args.base:
    previous_xml = subprocess.check_output(["git", "show", f"{args.base}:backend/pom.xml"], cwd=root, text=True)
    previous_version = ET.fromstring(previous_xml).findtext("m:version", namespaces=ns)
    def parts(value):
        match = re.match(r"([0-9]+)[.]([0-9]+)[.]([0-9]+)", value or "")
        assert match, "Não foi possível interpretar a versão base."
        return tuple(map(int, match.groups()))
    assert parts(version) > parts(previous_version), "Toda entrega deve incrementar a versão em relação à base do PR."
    print(f"Incremento validado: {previous_version} → {version}.")
