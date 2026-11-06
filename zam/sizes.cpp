// PORT(aarch64/g++14): diagnostic only, not part of libZAM.so.
// Prints aarch64/LP64 layout facts for the NOTES.md 64-bit audit.
#include <cstdio>
#include <cstdint>
#include "zam.h"
#include "natives.h"
#include "zamutil.h"
#define P(t) std::printf("%-28s %3zu  align %zu\n", #t, sizeof(t), alignof(t))
int main(){
  std::printf("void*=%zu  long=%zu  int=%zu  size_t=%zu  ptrdiff_t=%zu\n\n",
              sizeof(void*), sizeof(long), sizeof(int), sizeof(size_t),
              sizeof(ptrdiff_t));
  P(GcObject); P(GcArrayObject); P(GcRootLink); P(GcRoot); P(GcRootObject);
  P(GcForwardInfo); P(GcWeakObject); P(GcPage); P(GcSpace);
  P(RLink<ValueData>); P(Ring<ThreadData>);
  P(ValueData); P(TermData); P(VarData); P(SetData);
  P(ConstructorData); P(ConstrData); P(SchemaData); P(GlobalData);
  P(IntenData); P(ThreadData); P(GoalData); P(ChoiceData); P(MachineData);
  P(NativeNum); P(NativeDeno); P(NativeSeq);
  std::printf("\nGC_ALIGN = %d\n", GC_ALIGN);

  // Alignment probe: GC_ALIGN == 1 means the bump allocators round requests up
  // to a 1-byte boundary, i.e. not at all.  Every class type above is a
  // multiple of 8, so object allocation alone stays aligned -- but an
  // odd-sized char array (gcstrdup/ncstrdup/tmstrdup, and
  // GcHeap::collectArrPrim on a NativeDeno during GC) leaves the bump pointer
  // at an arbitrary offset, and every object allocated after it in that page
  // is misaligned.  See NOTES.md risk #1.
  GcHeap::init();
  void* o1 = GcHeap::noGcAlloc(24);      // an ordinary object
  void* st = GcHeap::noGcAllocArray(5);  // char[5] -- odd size
  void* o2 = GcHeap::noGcAlloc(24);      // the next object after it
  std::printf("\nalignment probe (NOTES.md risk #1)\n");
  std::printf("  obj1 %p  %% 8 = %lu\n", o1, (unsigned long)((uintptr_t)o1 % 8));
  std::printf("  str5 %p  %% 8 = %lu\n", st, (unsigned long)((uintptr_t)st % 8));
  std::printf("  obj2 %p  %% 8 = %lu   <-- MISALIGNED if nonzero\n",
              o2, (unsigned long)((uintptr_t)o2 % 8));
  return 0;
}
