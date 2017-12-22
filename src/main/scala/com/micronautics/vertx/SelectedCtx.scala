/*
 * Copyright 2017 Micronautics Research Corporation.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package com.micronautics.vertx

import _root_.model.persistence.QuillCacheImplicits

case object Ctx extends SelectedCtx with QuillCacheImplicits

trait SelectedCtx extends _root_.model.persistence.H2Ctx
