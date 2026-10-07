# needs: mkdir touch
mkdir -p d/e; touch d/a "d/e/b c"; find d -type f -print0 | while IFS= read -r -d "" f; do echo "[$f]"; done | sort
