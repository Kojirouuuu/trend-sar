#!/usr/bin/env bash
mkdir -p build

# directed-network-simulations 同様: -fopenmp で並列ビルド（未対応時は下の else で逐次ビルド）
if clang -fopenmp -std=c11 -Wall -Wextra -Wpedantic -O2 -g c-lang/regular-regular-sis.c -o build/regular-regular-sis -lm 2>/dev/null; then
  :
elif OMP_PREFIX=$(brew --prefix libomp 2>/dev/null) && [ -n "$OMP_PREFIX" ] && [ -d "$OMP_PREFIX/lib" ]; then
  clang -std=c11 -Wall -Wextra -Wpedantic -O2 -g \
    -Xpreprocessor -fopenmp -I"$OMP_PREFIX/include" -L"$OMP_PREFIX/lib" -lomp \
    c-lang/regular-regular-sis.c -o build/regular-regular-sis -lm
else
  clang -std=c11 -Wall -Wextra -Wpedantic -O2 -g c-lang/regular-regular-sis.c -o build/regular-regular-sis -lm
fi

./build/regular-regular-sis
