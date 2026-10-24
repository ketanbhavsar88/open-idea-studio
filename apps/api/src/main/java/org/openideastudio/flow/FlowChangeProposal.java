package org.openideastudio.flow;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Validated output of the propose-flow-change operation. */
record FlowChangeProposal(
        @NotBlank @Size(max = 500) String summary,
        @NotNull @Size(max = 30) List<@Valid @NotNull FlowOperation> operations,
        @Size(max = 10) List<@NotBlank @Size(max = 500) String> warnings) {
}
